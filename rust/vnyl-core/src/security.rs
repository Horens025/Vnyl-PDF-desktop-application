//! Security-hardened I/O, SSRF-safe PDF fetching, and OS credential store
//! helpers migrated from Vnyl's original Rust backend.

use std::net::{IpAddr, SocketAddr, ToSocketAddrs};
use std::time::Duration;

/// Allowed export file extensions (closed allowlist).
pub const EXPORT_EXTENSIONS: &[&str] = &[
    "pdf", "png", "jpg", "jpeg", "webp", "zip", "txt", "md", "html",
];

const SIGNING_CREDENTIAL_SERVICE: &str = "vnyl.signing.identity";
const MAX_SIGNING_IDENTITY_BASE64: usize = 2 * 1024 * 1024;

/// True if `ip` is loopback, private, link-local, CGNAT, multicast, or
/// otherwise not a globally routable public address.
pub fn is_disallowed_ip(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(v4) => {
            let [a, b, ..] = v4.octets();
            v4.is_unspecified()
                || v4.is_loopback()
                || v4.is_private()
                || v4.is_link_local()
                || v4.is_broadcast()
                || v4.is_documentation()
                || v4.is_multicast()
                || a == 0
                || (a == 100 && (b & 0xc0) == 64)
                || (a == 198 && (b & 0xfe) == 18)
                || a >= 240
        }
        IpAddr::V6(v6) => {
            if let Some(v4) = v6.to_ipv4_mapped() {
                return is_disallowed_ip(IpAddr::V4(v4));
            }
            let segs = v6.segments();
            if segs[..6].iter().all(|&s| s == 0) && (segs[6] != 0 || segs[7] > 1) {
                return is_disallowed_ip(IpAddr::V4(std::net::Ipv4Addr::new(
                    (segs[6] >> 8) as u8,
                    segs[6] as u8,
                    (segs[7] >> 8) as u8,
                    segs[7] as u8,
                )));
            }
            let first = segs[0];
            v6.is_unspecified()
                || v6.is_loopback()
                || v6.is_multicast()
                || (first & 0xfe00) == 0xfc00
                || (first & 0xffc0) == 0xfe80
        }
    }
}

#[derive(Debug)]
struct PinnedResolver(Vec<SocketAddr>);

impl ureq::unversioned::resolver::Resolver for PinnedResolver {
    fn resolve(
        &self,
        _uri: &ureq::http::Uri,
        _config: &ureq::config::Config,
        _timeout: ureq::unversioned::transport::NextTimeout,
    ) -> Result<ureq::unversioned::resolver::ResolvedSocketAddrs, ureq::Error> {
        let mut result = self.empty();
        for addr in self.0.iter().copied().take(16) {
            result.push(addr);
        }
        if result.is_empty() {
            Err(ureq::Error::HostNotFound)
        } else {
            Ok(result)
        }
    }
}

/// Whether `path`'s final extension is in `allowed` (case-insensitive).
pub fn has_allowed_extension(path: &str, allowed: &[&str]) -> bool {
    std::path::Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| allowed.iter().any(|a| a.eq_ignore_ascii_case(e)))
        .unwrap_or(false)
}

/// Write bytes atomically via a randomly named temp file in the target's
/// directory followed by `sync_all` + rename.
pub fn write_atomic(path: &str, contents: &[u8]) -> Result<(), String> {
    use std::io::Write;

    let target = std::path::Path::new(path);
    let dir = match target.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent,
        _ => std::path::Path::new("."),
    };
    let mut tmp = tempfile::Builder::new()
        .prefix(".vnyl-save-")
        .suffix(".tmp")
        .tempfile_in(dir)
        .map_err(|e| format!("Failed to write {path}: {e}"))?;
    tmp.write_all(contents)
        .map_err(|e| format!("Failed to write {path}: {e}"))?;
    tmp.as_file()
        .sync_all()
        .map_err(|e| format!("Failed to write {path}: {e}"))?;
    tmp.persist(target)
        .map(|_| ())
        .map_err(|e| format!("Failed to write {path}: {e}"))
}

/// Download a public PDF over http(s) with SSRF protection, pinned DNS
/// resolution, zero redirects, and a 512 MiB cap.
pub fn fetch_pdf(url: &str) -> Result<Vec<u8>, String> {
    const MAX_BYTES: u64 = 512 * 1024 * 1024;
    const CONNECT_TIMEOUT: Duration = Duration::from_secs(10);
    const READ_TIMEOUT: Duration = Duration::from_secs(60);

    let parsed = url::Url::parse(url).map_err(|e| format!("Invalid URL: {e}"))?;
    if !matches!(parsed.scheme(), "http" | "https") {
        return Err(format!("Unsupported URL scheme: {}", parsed.scheme()));
    }
    let host = parsed
        .host_str()
        .ok_or_else(|| "URL has no host".to_string())?
        .to_string();
    let port = parsed
        .port_or_known_default()
        .ok_or_else(|| "URL has no port".to_string())?;

    let addrs: Vec<SocketAddr> = (host.as_str(), port)
        .to_socket_addrs()
        .map_err(|e| format!("Could not resolve host: {e}"))?
        .collect();
    if addrs.is_empty() {
        return Err("Host did not resolve to any address".into());
    }
    if let Some(bad) = addrs.iter().find(|a| is_disallowed_ip(a.ip())) {
        return Err(format!(
            "Refusing to fetch from a local, private, or reserved address ({})",
            bad.ip()
        ));
    }

    let config = ureq::config::Config::builder()
        .max_redirects(0)
        .timeout_connect(Some(CONNECT_TIMEOUT))
        .timeout_recv_response(Some(READ_TIMEOUT))
        .timeout_recv_body(Some(READ_TIMEOUT))
        .build();
    let agent = ureq::Agent::with_parts(
        config,
        ureq::unversioned::transport::DefaultConnector::new(),
        PinnedResolver(addrs),
    );

    let mut resp = agent.get(url).call().map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("Server returned HTTP {}", resp.status().as_u16()));
    }
    resp.body_mut()
        .with_config()
        .limit(MAX_BYTES)
        .read_to_vec()
        .map_err(|e| e.to_string())
}

pub fn validate_signing_identity_id(id: &str) -> Result<(), String> {
    let valid = !id.is_empty()
        && id.len() <= 128
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-');
    if valid {
        Ok(())
    } else {
        Err("Invalid signing identity id".to_string())
    }
}

pub fn validate_signing_identity_base64(encoded: &str) -> Result<(), String> {
    let padding = encoded.len() - encoded.trim_end_matches('=').len();
    let valid = !encoded.is_empty()
        && encoded.len() <= MAX_SIGNING_IDENTITY_BASE64
        && encoded.len() % 4 == 0
        && padding <= 2
        && !encoded.trim_end_matches('=').bytes().any(|b| b == b'=')
        && encoded
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'+' || b == b'/' || b == b'=');
    if valid {
        Ok(())
    } else {
        Err("Invalid signing identity data".to_string())
    }
}

pub fn save_signing_identity(id: &str, p12_base64: &str) -> Result<(), String> {
    validate_signing_identity_id(id)?;
    validate_signing_identity_base64(p12_base64)?;
    let entry = keyring::Entry::new(SIGNING_CREDENTIAL_SERVICE, id)
        .map_err(|_| "Could not open secure storage".to_string())?;
    entry
        .set_password(p12_base64)
        .map_err(|_| "Could not save the signing identity in secure storage".to_string())
}

pub fn load_signing_identity(id: &str) -> Result<String, String> {
    validate_signing_identity_id(id)?;
    let entry = keyring::Entry::new(SIGNING_CREDENTIAL_SERVICE, id)
        .map_err(|_| "Could not open secure storage".to_string())?;
    entry.get_password().map_err(|error| match error {
        keyring::Error::NoEntry => "No saved signing identity found".to_string(),
        _ => "Could not read the signing identity from secure storage".to_string(),
    })
}

pub fn delete_signing_identity(id: &str) -> Result<(), String> {
    validate_signing_identity_id(id)?;
    let entry = keyring::Entry::new(SIGNING_CREDENTIAL_SERVICE, id)
        .map_err(|_| "Could not open secure storage".to_string())?;
    match entry.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(_) => Err("Could not remove the signing identity from secure storage".to_string()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::IpAddr;

    fn ip(s: &str) -> IpAddr {
        s.parse().expect("valid IP literal")
    }

    #[test]
    fn signing_identity_ids_are_strict() {
        for ok in ["id_123", "ABC-def-909", "a"] {
            assert!(validate_signing_identity_id(ok).is_ok(), "{ok}");
        }
        let long = "x".repeat(129);
        for bad in ["", "../etc/passwd", "a b", "a:b", long.as_str()] {
            assert!(validate_signing_identity_id(bad).is_err(), "{bad}");
        }
    }

    #[test]
    fn signing_identity_base64_is_bounded_and_well_formed() {
        assert!(validate_signing_identity_base64("AQIDBA==").is_ok());
        assert!(validate_signing_identity_base64("AAECAw==").is_ok());
        for bad in ["", "A", "AQIDBA=", "AQ=IDBA=", "AQIDB====", "AQID BA==", "!!!"] {
            assert!(validate_signing_identity_base64(bad).is_err(), "{bad}");
        }
    }

    #[test]
    fn export_extensions_are_a_closed_list() {
        for ok in ["a.png", "B.JPG", "x/y/z.zip", "notes.md", "C:\\out\\p.WebP", "part-1.PDF"] {
            assert!(has_allowed_extension(ok, EXPORT_EXTENSIONS), "{ok}");
        }
        for bad in ["a.exe", "a.bat", "a.pdf.exe", "noext", ""] {
            assert!(!has_allowed_extension(bad, EXPORT_EXTENSIONS), "{bad}");
        }
    }

    #[test]
    fn write_atomic_replaces_the_target_and_leaves_no_temp_file() {
        let dir = tempfile::tempdir().unwrap();
        let target = dir.path().join("out.png");
        std::fs::write(&target, b"old").unwrap();
        write_atomic(target.to_str().unwrap(), b"new bytes").unwrap();
        assert_eq!(std::fs::read(&target).unwrap(), b"new bytes");
        let leftovers = std::fs::read_dir(dir.path()).unwrap().count();
        assert_eq!(leftovers, 1);
    }

    #[test]
    fn blocks_private_and_reserved_ips() {
        for s in [
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "198.18.0.1", "0.0.0.0", "255.255.255.255", "224.0.0.1",
            "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1",
        ] {
            assert!(is_disallowed_ip(ip(s)), "{s} should be disallowed");
        }
    }

    #[test]
    fn allows_public_ips() {
        for s in ["1.1.1.1", "8.8.8.8", "140.82.112.3", "2606:4700:4700::1111"] {
            assert!(!is_disallowed_ip(ip(s)), "{s} should be allowed");
        }
    }
}
