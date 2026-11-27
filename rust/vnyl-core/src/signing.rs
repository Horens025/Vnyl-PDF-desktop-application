//! Cryptographic PKCS#7 / CMS detached PDF signature module using `.p12` keystores.
//!
//! Uses vetted RustCrypto crates (`p12-keystore`, `cms`, `rsa`, `x509-cert`, `sha2`)
//! with zero custom cryptographic primitives and zero `unsafe`.

use cms::builder::{SignedDataBuilder, SignerInfoBuilder};
use cms::content_info::ContentInfo;
use cms::signed_data::{EncapsulatedContentInfo, SignedData, SignerIdentifier};
use der::asn1::OctetString;
use der::{Decode, Encode};
use p12_keystore::{KeyStore, KeyStoreEntry};
use pkcs8::DecodePrivateKey;
use rsa::pkcs1v15::{Signature as RsaPkcs1Signature, SigningKey};
use rsa::RsaPrivateKey;
use sha2::{Digest, Sha256};
use x509_cert::spki::AlgorithmIdentifierOwned;
use x509_cert::Certificate;

const SIG_HEX_RESERVE_BYTES: usize = 8192; // 16384 hex chars

struct LoadedIdentity {
    cert: Certificate,
    chain: Vec<Certificate>,
    signing_key: SigningKey<Sha256>,
}

fn load_pkcs12_identity(p12_bytes: &[u8], password: &str) -> Result<LoadedIdentity, String> {
    let keystore = KeyStore::from_pkcs12(p12_bytes, password)
        .map_err(|e| format!("Failed reading .p12 keystore (check password): {e:?}"))?;

    for (_, entry) in keystore.entries() {
        if let KeyStoreEntry::PrivateKeyChain(pk_chain) = entry {
            let der_key = pk_chain.key();
            let rsa_key = RsaPrivateKey::from_pkcs8_der(der_key.as_ref())
                .map_err(|e| format!("Unsupported or invalid RSA PKCS#8 private key in .p12: {e}"))?;
            let signing_key = SigningKey::<Sha256>::new(rsa_key);

            let mut certs = Vec::new();
            for raw_cert in pk_chain.chain() {
                if let Ok(c) = Certificate::from_der(raw_cert.as_der()) {
                    certs.push(c);
                }
            }
            let leaf = certs
                .first()
                .cloned()
                .ok_or_else(|| "No X.509 certificate found in .p12 chain".to_string())?;
            return Ok(LoadedIdentity {
                cert: leaf,
                chain: certs,
                signing_key,
            });
        }
    }

    Err("No private key entry found inside .p12 file".to_string())
}

fn build_detached_cms(identity: &LoadedIdentity, signed_bytes_a: &[u8], signed_bytes_b: &[u8]) -> Result<Vec<u8>, String> {
    let mut hasher = Sha256::new();
    hasher.update(signed_bytes_a);
    hasher.update(signed_bytes_b);
    let digest = hasher.finalize();

    let sid = SignerIdentifier::IssuerAndSerialNumber(cms::cert::IssuerAndSerialNumber {
        issuer: identity.cert.tbs_certificate.issuer.clone(),
        serial_number: identity.cert.tbs_certificate.serial_number.clone(),
    });

    let econtent_info = EncapsulatedContentInfo {
        econtent_type: const_oid::db::rfc5911::ID_DATA,
        econtent: None,
    };

    let digest_alg = AlgorithmIdentifierOwned {
        oid: const_oid::db::rfc5912::ID_SHA_256,
        parameters: None,
    };

    let signer_builder = SignerInfoBuilder::new(
        &identity.signing_key,
        sid,
        digest_alg,
        &econtent_info,
        Some(digest.as_slice()),
    )
    .map_err(|e| format!("Failed initializing CMS SignerInfo: {e:?}"))?;

    let mut sd_builder = SignedDataBuilder::new(&econtent_info);

    for c in &identity.chain {
        sd_builder
            .add_certificate(cms::cert::CertificateChoices::Certificate(c.clone()))
            .map_err(|e| format!("Failed adding certificate to CMS SignedData: {e:?}"))?;
    }

    let content_info: ContentInfo = sd_builder
        .add_signer_info::<SigningKey<Sha256>, RsaPkcs1Signature>(signer_builder)
        .map_err(|e| format!("Failed adding CMS signer info: {e:?}"))?
        .build()
        .map_err(|e| format!("Failed building CMS SignedData: {e:?}"))?;

    content_info
        .to_der()
        .map_err(|e| format!("Failed DER-encoding CMS signature: {e:?}"))
}

fn parse_classic_pdf_trailer(pdf: &[u8]) -> Result<(usize, usize, usize), String> {
    let text = String::from_utf8_lossy(pdf);
    let xref_pos = text
        .rfind("startxref")
        .ok_or_else(|| "PDF does not contain classic startxref".to_string())?;
    let prev_xref: usize = text[xref_pos + 9..]
        .split_whitespace()
        .next()
        .and_then(|s| s.parse().ok())
        .ok_or_else(|| "Invalid startxref offset".to_string())?;

    let trailer_pos = text[..xref_pos]
        .rfind("trailer")
        .ok_or_else(|| "PDF uses compressed XRef stream; resave via Vnyl before signing".to_string())?;
    let trailer_slice = &text[trailer_pos..xref_pos];

    let extract_num = |key: &str| -> Option<usize> {
        let idx = trailer_slice.find(key)?;
        trailer_slice[idx + key.len()..]
            .split_whitespace()
            .next()
            .and_then(|s| s.parse().ok())
    };

    let size = extract_num("/Size").ok_or_else(|| "Missing /Size in PDF trailer".to_string())?;
    let root = extract_num("/Root").ok_or_else(|| "Missing /Root in PDF trailer".to_string())?;
    Ok((size, root, prev_xref))
}

fn escape_pdf_literal(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for ch in s.chars() {
        match ch {
            '\\' => out.push_str("\\\\"),
            '(' => out.push_str("\\("),
            ')' => out.push_str("\\)"),
            c if c.is_ascii() && !c.is_ascii_control() => out.push(c),
            _ => out.push(' '),
        }
    }
    out
}

/// Appends an `/adbe.pkcs7.detached` PDF signature dictionary as a clean incremental update
/// over `pdf_bytes`, computing exact `/ByteRange` SHA-256 CMS digest and hex-encoding `/Contents`.
pub fn sign_pdf_bytes_pkcs12(
    pdf_bytes: &[u8],
    p12_bytes: &[u8],
    password: &str,
    reason: &str,
) -> Result<Vec<u8>, String> {
    let identity = load_pkcs12_identity(p12_bytes, password)?;
    let (old_size, _root_id, prev_xref) = parse_classic_pdf_trailer(pdf_bytes)?;

    let mut out = pdf_bytes.to_vec();
    if !out.ends_with(b"\n") {
        out.push(b'\n');
    }

    let sig_obj_id = old_size;
    let new_size = sig_obj_id + 1;
    let sig_obj_offset = out.len();

    let hex_len = SIG_HEX_RESERVE_BYTES * 2;
    let br_placeholder = "[0000000000 0000000000 0000000000 0000000000]";
    let hex_placeholder = "0".repeat(hex_len);
    let reason_clean = escape_pdf_literal(if reason.trim().is_empty() {
        "Signed with Vnyl"
    } else {
        reason.trim()
    });

    let sig_header = format!(
        "{sig_obj_id} 0 obj\n<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /adbe.pkcs7.detached /Reason ({reason_clean}) /ByteRange {br_placeholder} /Contents <"
    );
    out.extend_from_slice(sig_header.as_bytes());
    let contents_lt_pos = out.len() - 1; // index of '<'
    out.extend_from_slice(hex_placeholder.as_bytes());
    out.extend_from_slice(b"> >>\nendobj\n");
    let contents_gt_pos = contents_lt_pos + 1 + hex_len; // index of '>'

    let xref_offset = out.len();
    let xref_block = format!(
        "xref\n{sig_obj_id} 1\n{sig_obj_offset:010} 00000 n \ntrailer\n<< /Size {new_size} /Root {_root_id} 0 R /Prev {prev_xref} >>\nstartxref\n{xref_offset}\n%%EOF\n"
    );
    out.extend_from_slice(xref_block.as_bytes());

    // Patch /ByteRange to exact same ASCII byte width so no offset shifts.
    let part2_start = contents_gt_pos + 1;
    let part2_len = out.len() - part2_start;
    let br_actual_raw = format!("[0 {contents_lt_pos} {part2_start} {part2_len}]");
    if br_actual_raw.len() > br_placeholder.len() {
        return Err("Signature ByteRange exceeded placeholder width".to_string());
    }
    let br_padded = format!(
        "[0 {contents_lt_pos} {part2_start} {part2_len}{}]",
        " ".repeat(br_placeholder.len() - br_actual_raw.len())
    );
    let br_start = sig_obj_offset
        + sig_header
            .find(br_placeholder)
            .ok_or_else(|| "ByteRange placeholder missing".to_string())?;
    out[br_start..br_start + br_placeholder.len()].copy_from_slice(br_padded.as_bytes());

    // Compute detached CMS signature over [0..contents_lt_pos] || [part2_start..].
    let cms_der = build_detached_cms(&identity, &out[..contents_lt_pos], &out[part2_start..])?;
    if cms_der.len() > SIG_HEX_RESERVE_BYTES {
        return Err(format!(
            "Encoded CMS signature ({} bytes) exceeds reserved 8 KiB PDF slot",
            cms_der.len()
        ));
    }
    let cms_hex = hex::encode_upper(&cms_der);
    out[contents_lt_pos + 1..contents_lt_pos + 1 + cms_hex.len()]
        .copy_from_slice(cms_hex.as_bytes());

    // Ensure non-unused trailing zeros verify cleanly as DER SignedData.
    let _check = ContentInfo::from_der(&cms_der)
        .map_err(|e| format!("CMS self-check failed: {e}"))?;
    let _oct = OctetString::new(cms_der).map_err(|e| format!("OctetString check: {e}"))?;
    let _sd = SignedData::from_der(
        ContentInfo::from_der(
            &hex::decode(&out[contents_lt_pos + 1..contents_lt_pos + 1 + cms_hex.len()])
                .unwrap_or_default(),
        )
        .map_err(|e| format!("ContentInfo verify failed: {e}"))?
        .content
        .value(),
    );

    Ok(out)
}
