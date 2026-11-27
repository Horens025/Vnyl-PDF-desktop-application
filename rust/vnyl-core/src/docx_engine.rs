//! Native Word (.docx) reader, writer, PDF conversion, and Office fallback engine.
//!
//! Uses pure-Rust `zip` + `quick-xml` with strict size caps (anti-zip-bomb) and zero `unsafe`.

use std::collections::HashMap;
use std::io::{Cursor, Read, Write};
use std::path::Path;
use std::process::Command;

use quick_xml::events::Event;
use quick_xml::Reader;
use serde::{Deserialize, Serialize};
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

use crate::security;

const MAX_DOCX_BYTES: u64 = 64 * 1024 * 1024;
const MAX_XML_ENTRY_BYTES: usize = 32 * 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DocxRun {
    pub text: String,
    #[serde(default)]
    pub bold: bool,
    #[serde(default)]
    pub italic: bool,
    #[serde(default)]
    pub underline: bool,
}

fn default_kind() -> String {
    "paragraph".to_string()
}

fn default_align() -> String {
    "left".to_string()
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DocxBlock {
    pub id: String,
    /// "h1", "h2", "h3", "paragraph", "bullet", "numbered", "quote", "table"
    #[serde(default = "default_kind")]
    pub kind: String,
    #[serde(default = "default_align")]
    pub align: String,
    #[serde(default)]
    pub runs: Vec<DocxRun>,
    #[serde(default)]
    pub rows: Vec<Vec<String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DocxDocument {
    pub title: String,
    pub blocks: Vec<DocxBlock>,
}

fn local_name(bytes: &[u8]) -> &[u8] {
    match bytes.iter().rposition(|&b| b == b':') {
        Some(idx) => &bytes[idx + 1..],
        None => bytes,
    }
}

fn attr_val(reader: &Reader<&[u8]>, ev: &quick_xml::events::BytesStart<'_>, target: &[u8]) -> Option<String> {
    for attr in ev.attributes().flatten() {
        if local_name(attr.key.as_ref()) == target {
            if let Ok(v) = attr.decode_and_unescape_value(reader.decoder()) {
                return Some(v.into_owned());
            }
        }
    }
    None
}

fn read_zip_entry_bounded(archive: &mut ZipArchive<Cursor<&[u8]>>, name: &str) -> Option<String> {
    let mut entry = archive.by_name(name).ok()?;
    let mut buf = Vec::new();
    let mut limited = (&mut entry).take((MAX_XML_ENTRY_BYTES + 1) as u64);
    if limited.read_to_end(&mut buf).is_err() || buf.len() > MAX_XML_ENTRY_BYTES {
        return None;
    }
    String::from_utf8(buf).ok()
}

/// Parses `word/numbering.xml` abstract formats so `numId` maps to `"bullet"` or `"numbered"`.
fn parse_numbering_kinds(xml: &str) -> HashMap<String, String> {
    let mut abstract_fmt: HashMap<String, String> = HashMap::new();
    let mut num_to_abstract: HashMap<String, String> = HashMap::new();

    let mut reader = Reader::from_str(xml);
    reader.config_mut().trim_text(false);

    let mut cur_abstract_id: Option<String> = None;
    let mut cur_num_id: Option<String> = None;

    while let Ok(ev) = reader.read_event() {
        match ev {
            Event::Start(e) | Event::Empty(e) => {
                let name = e.name();
                let ln = local_name(name.as_ref());
                if ln == b"abstractNum" {
                    cur_abstract_id = attr_val(&reader, &e, b"abstractNumId");
                } else if ln == b"numFmt" {
                    if let (Some(aid), Some(val)) = (&cur_abstract_id, attr_val(&reader, &e, b"val")) {
                        let mapped = if val == "bullet" { "bullet" } else { "numbered" };
                        abstract_fmt.entry(aid.clone()).or_insert_with(|| mapped.to_string());
                    }
                } else if ln == b"num" {
                    cur_num_id = attr_val(&reader, &e, b"numId");
                } else if ln == b"abstractNumId" {
                    if let (Some(nid), Some(val)) = (&cur_num_id, attr_val(&reader, &e, b"val")) {
                        num_to_abstract.insert(nid.clone(), val);
                    }
                }
            }
            Event::End(e) => {
                let name = e.name();
                let ln = local_name(name.as_ref());
                if ln == b"abstractNum" {
                    cur_abstract_id = None;
                } else if ln == b"num" {
                    cur_num_id = None;
                }
            }
            Event::Eof => break,
            _ => {}
        }
    }

    let mut out = HashMap::new();
    for (nid, aid) in num_to_abstract {
        let kind = abstract_fmt
            .get(&aid)
            .cloned()
            .unwrap_or_else(|| "bullet".to_string());
        out.insert(nid, kind);
    }
    out
}

pub fn parse_docx_file(path: &str) -> Result<DocxDocument, String> {
    let p = Path::new(path);
    let meta = std::fs::metadata(p).map_err(|e| format!("Failed to stat '{path}': {e}"))?;
    if meta.len() > MAX_DOCX_BYTES {
        return Err("DOCX file exceeds maximum supported size (64 MB)".to_string());
    }
    let bytes = std::fs::read(p).map_err(|e| format!("Failed reading '{path}': {e}"))?;
    let title = p
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("Document.docx")
        .to_string();
    parse_docx_bytes(&bytes, &title)
}

pub fn parse_docx_bytes(bytes: &[u8], title_fallback: &str) -> Result<DocxDocument, String> {
    if bytes.len() as u64 > MAX_DOCX_BYTES {
        return Err("DOCX payload too large".to_string());
    }
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor).map_err(|e| format!("Invalid DOCX archive: {e}"))?;

    let numbering_xml = read_zip_entry_bounded(&mut archive, "word/numbering.xml").unwrap_or_default();
    let num_kinds = if numbering_xml.is_empty() {
        HashMap::new()
    } else {
        parse_numbering_kinds(&numbering_xml)
    };

    let doc_xml = read_zip_entry_bounded(&mut archive, "word/document.xml")
        .ok_or_else(|| "Missing word/document.xml inside DOCX package".to_string())?;

    let mut blocks = Vec::new();
    let mut reader = Reader::from_str(&doc_xml);
    reader.config_mut().trim_text(false);

    let mut in_table = false;
    let mut table_rows: Vec<Vec<String>> = Vec::new();
    let mut cur_row: Vec<String> = Vec::new();
    let mut in_cell = false;
    let mut cell_text = String::new();

    let mut in_p = false;
    let mut p_kind = "paragraph".to_string();
    let mut p_align = "left".to_string();
    let mut p_runs: Vec<DocxRun> = Vec::new();

    let mut in_run = false;
    let mut in_rpr = false;
    let mut r_bold = false;
    let mut r_italic = false;
    let mut r_underline = false;
    let mut r_text = String::new();
    let mut in_text_node = false;
    let mut block_seq = 1usize;

    let push_run = |runs: &mut Vec<DocxRun>, text: String, b: bool, i: bool, u: bool| {
        if text.is_empty() {
            return;
        }
        if let Some(last) = runs.last_mut() {
            if last.bold == b && last.italic == i && last.underline == u {
                last.text.push_str(&text);
                return;
            }
        }
        runs.push(DocxRun {
            text,
            bold: b,
            italic: i,
            underline: u,
        });
    };

    while let Ok(ev) = reader.read_event() {
        match ev {
            Event::Start(e) => {
                let name = e.name();
                let ln = local_name(name.as_ref());
                match ln {
                    b"tbl" => {
                        in_table = true;
                        table_rows.clear();
                    }
                    b"tr" if in_table => cur_row.clear(),
                    b"tc" if in_table => {
                        in_cell = true;
                        cell_text.clear();
                    }
                    b"p" if in_table && in_cell => {
                        if !cell_text.is_empty() {
                            cell_text.push(' ');
                        }
                    }
                    b"p" if !in_table => {
                        in_p = true;
                        p_kind = "paragraph".to_string();
                        p_align = "left".to_string();
                        p_runs.clear();
                    }
                    b"r" if in_p || in_cell => {
                        in_run = true;
                        in_rpr = false;
                        r_bold = false;
                        r_italic = false;
                        r_underline = false;
                        r_text.clear();
                    }
                    b"rPr" if in_run => in_rpr = true,
                    b"t" | b"delText" if in_run => in_text_node = true,
                    b"pStyle" if in_p => {
                        if let Some(val) = attr_val(&reader, &e, b"val") {
                            let low = val.to_ascii_lowercase();
                            if low == "title" || low == "heading1" || low == "h1" {
                                p_kind = "h1".to_string();
                            } else if low == "subtitle" || low == "heading2" || low == "h2" {
                                p_kind = "h2".to_string();
                            } else if low.starts_with("heading") || low == "h3" {
                                p_kind = "h3".to_string();
                            } else if low.contains("quote") {
                                p_kind = "quote".to_string();
                            } else if low.contains("listbullet") {
                                p_kind = "bullet".to_string();
                            } else if low.contains("listnumber") {
                                p_kind = "numbered".to_string();
                            }
                        }
                    }
                    b"numId" if in_p => {
                        if let Some(val) = attr_val(&reader, &e, b"val") {
                            if val != "0" {
                                p_kind = num_kinds
                                    .get(&val)
                                    .cloned()
                                    .unwrap_or_else(|| "bullet".to_string());
                            }
                        }
                    }
                    b"jc" if in_p => {
                        if let Some(val) = attr_val(&reader, &e, b"val") {
                            match val.as_str() {
                                "center" => p_align = "center".to_string(),
                                "right" | "end" => p_align = "right".to_string(),
                                _ => p_align = "left".to_string(),
                            }
                        }
                    }
                    b"b" if in_rpr => {
                        let off = attr_val(&reader, &e, b"val")
                            .map(|v| v == "0" || v.eq_ignore_ascii_case("false"))
                            .unwrap_or(false);
                        r_bold = !off;
                    }
                    b"i" if in_rpr => {
                        let off = attr_val(&reader, &e, b"val")
                            .map(|v| v == "0" || v.eq_ignore_ascii_case("false"))
                            .unwrap_or(false);
                        r_italic = !off;
                    }
                    b"u" if in_rpr => {
                        let off = attr_val(&reader, &e, b"val")
                            .map(|v| v == "none" || v == "0" || v.eq_ignore_ascii_case("false"))
                            .unwrap_or(false);
                        r_underline = !off;
                    }
                    _ => {}
                }
            }
            Event::Empty(e) => {
                let name = e.name();
                let ln = local_name(name.as_ref());
                match ln {
                    b"pStyle" if in_p => {
                        if let Some(val) = attr_val(&reader, &e, b"val") {
                            let low = val.to_ascii_lowercase();
                            if low == "title" || low == "heading1" || low == "h1" {
                                p_kind = "h1".to_string();
                            } else if low == "subtitle" || low == "heading2" || low == "h2" {
                                p_kind = "h2".to_string();
                            } else if low.starts_with("heading") || low == "h3" {
                                p_kind = "h3".to_string();
                            } else if low.contains("quote") {
                                p_kind = "quote".to_string();
                            } else if low.contains("listbullet") {
                                p_kind = "bullet".to_string();
                            } else if low.contains("listnumber") {
                                p_kind = "numbered".to_string();
                            }
                        }
                    }
                    b"numId" if in_p => {
                        if let Some(val) = attr_val(&reader, &e, b"val") {
                            if val != "0" {
                                p_kind = num_kinds
                                    .get(&val)
                                    .cloned()
                                    .unwrap_or_else(|| "bullet".to_string());
                            }
                        }
                    }
                    b"jc" if in_p => {
                        if let Some(val) = attr_val(&reader, &e, b"val") {
                            match val.as_str() {
                                "center" => p_align = "center".to_string(),
                                "right" | "end" => p_align = "right".to_string(),
                                _ => p_align = "left".to_string(),
                            }
                        }
                    }
                    b"b" if in_rpr => {
                        let off = attr_val(&reader, &e, b"val")
                            .map(|v| v == "0" || v.eq_ignore_ascii_case("false"))
                            .unwrap_or(false);
                        r_bold = !off;
                    }
                    b"i" if in_rpr => {
                        let off = attr_val(&reader, &e, b"val")
                            .map(|v| v == "0" || v.eq_ignore_ascii_case("false"))
                            .unwrap_or(false);
                        r_italic = !off;
                    }
                    b"u" if in_rpr => {
                        let off = attr_val(&reader, &e, b"val")
                            .map(|v| v == "none" || v == "0" || v.eq_ignore_ascii_case("false"))
                            .unwrap_or(false);
                        r_underline = !off;
                    }
                    b"br" if in_run => r_text.push('\n'),
                    b"tab" if in_run => r_text.push('\t'),
                    _ => {}
                }
            }
            Event::Text(t) if in_text_node => {
                if let Ok(decoded) = t.unescape() {
                    r_text.push_str(&decoded);
                }
            }
            Event::End(e) => {
                let name = e.name();
                let ln = local_name(name.as_ref());
                match ln {
                    b"t" | b"delText" => in_text_node = false,
                    b"rPr" => in_rpr = false,
                    b"r" if in_run => {
                        in_run = false;
                        in_text_node = false;
                        let text = std::mem::take(&mut r_text);
                        if in_cell {
                            cell_text.push_str(&text);
                        } else if in_p {
                            push_run(&mut p_runs, text, r_bold, r_italic, r_underline);
                        }
                    }
                    b"p" if in_p => {
                        in_p = false;
                        let runs = std::mem::take(&mut p_runs);
                        let runs = if runs.is_empty() {
                            vec![DocxRun {
                                text: String::new(),
                                bold: false,
                                italic: false,
                                underline: false,
                            }]
                        } else {
                            runs
                        };
                        blocks.push(DocxBlock {
                            id: format!("b{block_seq}"),
                            kind: std::mem::replace(&mut p_kind, "paragraph".to_string()),
                            align: std::mem::replace(&mut p_align, "left".to_string()),
                            runs,
                            rows: Vec::new(),
                        });
                        block_seq += 1;
                    }
                    b"tc" if in_cell => {
                        in_cell = false;
                        cur_row.push(cell_text.trim().to_string());
                        cell_text.clear();
                    }
                    b"tr" if in_table => {
                        if !cur_row.is_empty() {
                            table_rows.push(std::mem::take(&mut cur_row));
                        }
                    }
                    b"tbl" if in_table => {
                        in_table = false;
                        if !table_rows.is_empty() {
                            blocks.push(DocxBlock {
                                id: format!("b{block_seq}"),
                                kind: "table".to_string(),
                                align: "left".to_string(),
                                runs: Vec::new(),
                                rows: std::mem::take(&mut table_rows),
                            });
                            block_seq += 1;
                        }
                    }
                    _ => {}
                }
            }
            Event::Eof => break,
            _ => {}
        }
    }

    if blocks.is_empty() {
        blocks.push(DocxBlock {
            id: "b1".to_string(),
            kind: "paragraph".to_string(),
            align: "left".to_string(),
            runs: vec![DocxRun {
                text: String::new(),
                bold: false,
                italic: false,
                underline: false,
            }],
            rows: Vec::new(),
        });
    }

    Ok(DocxDocument {
        title: title_fallback.to_string(),
        blocks,
    })
}

fn xml_escape(s: &str) -> String {
    quick_xml::escape::escape(s).into_owned()
}

pub fn encode_docx_bytes(doc: &DocxDocument) -> Result<Vec<u8>, String> {
    let mut cursor = Cursor::new(Vec::new());
    {
        let mut zip = ZipWriter::new(&mut cursor);
        let opts = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

        let content_types = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>
</Types>"#;

        let rels = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"#;

        let doc_rels = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>
</Relationships>"#;

        let styles_xml = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="40"/><w:szCs w:val="40"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:before="200" w:after="80"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:before="160" w:after="60"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Quote">
    <w:name w:val="Quote"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:ind w:left="540"/><w:spacing w:before="100" w:after="100"/></w:pPr>
    <w:rPr><w:i/><w:color w:val="555555"/></w:rPr>
  </w:style>
</w:styles>"#;

        let numbering_xml = r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:abstractNum w:abstractNumId="1">
    <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
  </w:abstractNum>
  <w:abstractNum w:abstractNumId="2">
    <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
  </w:abstractNum>
  <w:num w:numId="1"><w:abstractNumId w:val="1"/></w:num>
  <w:num w:numId="2"><w:abstractNumId w:val="2"/></w:num>
</w:numbering>"#;

        let mut body = String::from(
            r#"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>"#,
        );

        for b in &doc.blocks {
            if b.kind == "table" {
                body.push_str("<w:tbl><w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/><w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/><w:left w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/><w:right w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/><w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"DDDDDD\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"DDDDDD\"/></w:tblBorders></w:tblPr>");
                for row in &b.rows {
                    body.push_str("<w:tr>");
                    for cell in row {
                        body.push_str("<w:tc><w:p><w:r><w:t xml:space=\"preserve\">");
                        body.push_str(&xml_escape(cell));
                        body.push_str("</w:t></w:r></w:p></w:tc>");
                    }
                    body.push_str("</w:tr>");
                }
                body.push_str("</w:tbl>");
                continue;
            }

            body.push_str("<w:p><w:pPr>");
            match b.kind.as_str() {
                "h1" => body.push_str("<w:pStyle w:val=\"Heading1\"/>"),
                "h2" => body.push_str("<w:pStyle w:val=\"Heading2\"/>"),
                "h3" => body.push_str("<w:pStyle w:val=\"Heading3\"/>"),
                "quote" => body.push_str("<w:pStyle w:val=\"Quote\"/>"),
                "bullet" => body.push_str("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"),
                "numbered" => body.push_str("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"2\"/></w:numPr>"),
                _ => body.push_str("<w:pStyle w:val=\"Normal\"/><w:spacing w:after=\"120\"/>"),
            }
            if b.align == "center" || b.align == "right" {
                body.push_str(&format!("<w:jc w:val=\"{}\"/>", b.align));
            }
            body.push_str("</w:pPr>");

            let runs: Vec<DocxRun> = if b.runs.is_empty() {
                vec![DocxRun {
                    text: String::new(),
                    bold: false,
                    italic: false,
                    underline: false,
                }]
            } else {
                b.runs.clone()
            };

            for run in runs {
                body.push_str("<w:r>");
                if run.bold || run.italic || run.underline {
                    body.push_str("<w:rPr>");
                    if run.bold {
                        body.push_str("<w:b/>");
                    }
                    if run.italic {
                        body.push_str("<w:i/>");
                    }
                    if run.underline {
                        body.push_str("<w:u w:val=\"single\"/>");
                    }
                    body.push_str("</w:rPr>");
                }
                for (li, seg) in run.text.split('\n').enumerate() {
                    if li > 0 {
                        body.push_str("<w:br/>");
                    }
                    body.push_str("<w:t xml:space=\"preserve\">");
                    body.push_str(&xml_escape(seg));
                    body.push_str("</w:t>");
                }
                body.push_str("</w:r>");
            }
            body.push_str("</w:p>");
        }

        body.push_str("<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>");

        for (name, content) in [
            ("[Content_Types].xml", content_types),
            ("_rels/.rels", rels),
            ("word/_rels/document.xml.rels", doc_rels),
            ("word/styles.xml", styles_xml),
            ("word/numbering.xml", numbering_xml),
            ("word/document.xml", body.as_str()),
        ] {
            zip.start_file(name, opts)
                .map_err(|e| format!("Failed creating zip entry '{name}': {e}"))?;
            zip.write_all(content.as_bytes())
                .map_err(|e| format!("Failed writing zip entry '{name}': {e}"))?;
        }
        zip.finish()
            .map_err(|e| format!("Failed finalizing DOCX zip: {e}"))?;
    }
    Ok(cursor.into_inner())
}

pub fn save_docx_file(doc: &DocxDocument, dest_path: &str) -> Result<(), String> {
    let bytes = encode_docx_bytes(doc)?;
    security::write_atomic(dest_path, &bytes)
}

#[derive(Debug, Clone, Serialize)]
pub struct OfficeConverterInfo {
    pub available: bool,
    pub engine: String,
    pub path: Option<String>,
}

pub fn detect_office_converter() -> OfficeConverterInfo {
    let candidates = [
        ("LibreOffice", r"C:\Program Files\LibreOffice\program\soffice.exe"),
        ("LibreOffice", r"C:\Program Files (x86)\LibreOffice\program\soffice.exe"),
        ("Microsoft Word", r"C:\Program Files\Microsoft Office\root\Office16\WINWORD.EXE"),
        ("Microsoft Word", r"C:\Program Files (x86)\Microsoft Office\root\Office16\WINWORD.EXE"),
    ];
    for (engine, p) in candidates {
        if Path::new(p).is_file() {
            return OfficeConverterInfo {
                available: true,
                engine: engine.to_string(),
                path: Some(p.to_string()),
            };
        }
    }
    if let Ok(out) = Command::new("where").arg("soffice").output() {
        if out.status.success() {
            let line = String::from_utf8_lossy(&out.stdout)
                .lines()
                .next()
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty());
            if let Some(found) = line {
                return OfficeConverterInfo {
                    available: true,
                    engine: "LibreOffice".to_string(),
                    path: Some(found),
                };
            }
        }
    }
    OfficeConverterInfo {
        available: false,
        engine: "Built-in".to_string(),
        path: None,
    }
}

/// Converts a `.doc` or `.docx` file to PDF bytes using Microsoft Word or LibreOffice if installed.
pub fn convert_word_file_via_office(src_path: &str) -> Result<Vec<u8>, String> {
    let src = Path::new(src_path);
    if !src.is_file() {
        return Err(format!("Word file not found: {src_path}"));
    }
    let conv = detect_office_converter();
    if !conv.available {
        return Err(
            "Neither LibreOffice nor Microsoft Word was detected for legacy/exact Office conversion"
                .to_string(),
        );
    }

    let tmp_dir = tempfile::tempdir().map_err(|e| format!("Failed creating temp dir: {e}"))?;
    let stem = src
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("converted");
    let out_pdf = tmp_dir.path().join(format!("{stem}.pdf"));

    if conv.engine == "LibreOffice" {
        let exe = conv.path.ok_or_else(|| "Missing soffice path".to_string())?;
        let status = Command::new(&exe)
            .args(["--headless", "--convert-to", "pdf", "--outdir"])
            .arg(tmp_dir.path())
            .arg(src)
            .status()
            .map_err(|e| format!("Failed launching LibreOffice: {e}"))?;
        if !status.success() || !out_pdf.is_file() {
            return Err("LibreOffice conversion did not produce a PDF".to_string());
        }
    } else {
        // Use Word COM automation safely passing arguments via env vars (zero PS injection risk).
        let script = r#"
$ErrorActionPreference = 'Stop'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    $doc = $word.Documents.Open($env:VNYL_WORD_SRC, $false, $true)
    $doc.ExportAsFixedFormat($env:VNYL_WORD_OUT, 17)
    $doc.Close($false)
} finally {
    $word.Quit()
}
"#;
        let status = Command::new("powershell")
            .args(["-NoProfile", "-NonInteractive", "-Command", script])
            .env("VNYL_WORD_SRC", src)
            .env("VNYL_WORD_OUT", &out_pdf)
            .status()
            .map_err(|e| format!("Failed invoking Word automation: {e}"))?;
        if !status.success() || !out_pdf.is_file() {
            return Err("Microsoft Word PDF export did not produce output".to_string());
        }
    }

    std::fs::read(&out_pdf).map_err(|e| format!("Failed reading converted PDF: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn docx_roundtrip_preserves_blocks_and_formatting() {
        let doc = DocxDocument {
            title: "Spec.docx".to_string(),
            blocks: vec![
                DocxBlock {
                    id: "b1".to_string(),
                    kind: "h1".to_string(),
                    align: "left".to_string(),
                    runs: vec![DocxRun {
                        text: "Architectural Brief".to_string(),
                        bold: true,
                        italic: false,
                        underline: false,
                    }],
                    rows: Vec::new(),
                },
                DocxBlock {
                    id: "b2".to_string(),
                    kind: "bullet".to_string(),
                    align: "left".to_string(),
                    runs: vec![
                        DocxRun {
                            text: "Pure Rust ".to_string(),
                            bold: false,
                            italic: false,
                            underline: false,
                        },
                        DocxRun {
                            text: "OOXML".to_string(),
                            bold: true,
                            italic: true,
                            underline: true,
                        },
                    ],
                    rows: Vec::new(),
                },
                DocxBlock {
                    id: "b3".to_string(),
                    kind: "table".to_string(),
                    align: "left".to_string(),
                    runs: Vec::new(),
                    rows: vec![
                        vec!["Key".to_string(), "Value".to_string()],
                        vec!["Mode".to_string(), "Monochrome".to_string()],
                    ],
                },
            ],
        };

        let tmp = tempfile::tempdir().unwrap();
        let path = tmp.path().join("roundtrip.docx");
        save_docx_file(&doc, path.to_str().unwrap()).expect("save docx");
        let parsed = parse_docx_file(path.to_str().unwrap()).expect("parse docx");
        assert_eq!(parsed.blocks.len(), 3);
        assert_eq!(parsed.blocks[0].kind, "h1");
        assert_eq!(parsed.blocks[0].runs[0].text, "Architectural Brief");
        assert_eq!(parsed.blocks[1].kind, "bullet");
        assert_eq!(parsed.blocks[1].runs[1].underline, true);
        assert_eq!(parsed.blocks[2].kind, "table");
        assert_eq!(parsed.blocks[2].rows[1][1], "Monochrome");
    }
}
