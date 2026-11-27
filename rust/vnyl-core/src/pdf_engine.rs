//! PDFium-backed PDF document engine for Vnyl.
//!
//! Runs a dedicated PDFium worker thread that owns the `Pdfium` instance and
//! all open `PdfDocument<'static>` handles. Callers communicate over typed MPSC
//! channels, guaranteeing PDFium thread affinity with zero `unsafe` code.

use std::collections::HashMap;
use std::io::Cursor;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::mpsc;

use once_cell::sync::OnceCell;
use pdfium_render::prelude::*;
use serde::Serialize;

use crate::docx_engine::{self, DocxBlock, DocxDocument, DocxRun};
use crate::security;
use crate::signing;

static NEXT_DOC_ID: AtomicU64 = AtomicU64::new(1);
static WORKER_TX: OnceCell<mpsc::Sender<PdfCommand>> = OnceCell::new();

#[derive(Debug, Clone, Serialize)]
pub struct PageSizeInfo {
    pub width_pts: f32,
    pub height_pts: f32,
}

#[derive(Debug, Clone, Serialize)]
pub struct DocumentOpenInfo {
    pub doc_id: u64,
    pub title: String,
    pub page_count: usize,
    pub pages: Vec<PageSizeInfo>,
}

#[derive(Debug, Clone, Serialize)]
pub struct DocumentMetadataInfo {
    pub title: String,
    pub author: String,
    pub subject: String,
    pub creator: String,
    pub producer: String,
    pub page_count: usize,
}

#[derive(Debug, Clone, Serialize)]
pub struct OutlineItemInfo {
    pub title: String,
    pub page_index: Option<usize>,
    pub children: Vec<OutlineItemInfo>,
}

#[derive(Debug, Clone, Serialize)]
pub struct SearchHitInfo {
    pub page_index: usize,
    pub match_text: String,
    /// Normalized [0.0..1.0] top-left coordinates in page space.
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

#[derive(Debug, Clone, Serialize)]
pub struct TextSpanInfo {
    pub text: String,
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

#[derive(Debug, Clone, Serialize)]
pub struct PageLinkInfo {
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
    pub uri: Option<String>,
    pub dest_page: Option<usize>,
}

#[derive(Debug, Clone, Serialize)]
pub struct AnnotationItemInfo {
    pub page_index: usize,
    pub annot_index: usize,
    pub kind: String,
    pub contents: String,
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

#[derive(Debug, Clone, Serialize)]
pub struct FormFieldInfo {
    pub page_index: usize,
    pub annot_index: usize,
    pub kind: String,
    pub name: String,
    pub value: String,
    pub checked: bool,
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

enum PdfCommand {
    OpenBytes {
        bytes: Vec<u8>,
        title_fallback: String,
        password: Option<String>,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    Close {
        doc_id: u64,
        reply: mpsc::SyncSender<bool>,
    },
    RenderPage {
        doc_id: u64,
        page_index: usize,
        width_px: i32,
        height_px: i32,
        reply: mpsc::SyncSender<Result<Vec<u8>, String>>,
    },
    RenderPageDpi {
        doc_id: u64,
        page_index: usize,
        dpi: u32,
        reply: mpsc::SyncSender<Result<(u32, u32, Vec<u8>), String>>,
    },
    GetOutline {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<Vec<OutlineItemInfo>, String>>,
    },
    GetMetadata {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<DocumentMetadataInfo, String>>,
    },
    GetPageText {
        doc_id: u64,
        page_index: usize,
        reply: mpsc::SyncSender<Result<String, String>>,
    },
    GetPageTextSpans {
        doc_id: u64,
        page_index: usize,
        reply: mpsc::SyncSender<Result<Vec<TextSpanInfo>, String>>,
    },
    GetPageLinks {
        doc_id: u64,
        page_index: usize,
        reply: mpsc::SyncSender<Result<Vec<PageLinkInfo>, String>>,
    },
    GetAnnotations {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<Vec<AnnotationItemInfo>, String>>,
    },
    DeleteAnnotation {
        doc_id: u64,
        page_index: usize,
        annot_index: usize,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    Search {
        doc_id: u64,
        query: String,
        max_hits: usize,
        reply: mpsc::SyncSender<Result<Vec<SearchHitInfo>, String>>,
    },
    AddHighlight {
        doc_id: u64,
        page_index: usize,
        left: f32,
        top: f32,
        right: f32,
        bottom: f32,
        argb: u32,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    AddInkStroke {
        doc_id: u64,
        page_index: usize,
        points_norm: Vec<(f32, f32)>,
        argb: u32,
        stroke_width_pts: f32,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    AddTextNote {
        doc_id: u64,
        page_index: usize,
        norm_x: f32,
        norm_y: f32,
        text: String,
        font_size_pts: f32,
        argb: u32,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    AddImageBytes {
        doc_id: u64,
        page_index: usize,
        image_bytes: Vec<u8>,
        norm_x: f32,
        norm_y: f32,
        norm_width: f32,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    RotatePage {
        doc_id: u64,
        page_index: usize,
        delta_quarters: i32,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    DeletePage {
        doc_id: u64,
        page_index: usize,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    ReorderPages {
        doc_id: u64,
        order: Vec<usize>,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    ExtractPagesBytes {
        doc_id: u64,
        pages_one_based: String,
        reply: mpsc::SyncSender<Result<Vec<u8>, String>>,
    },
    MergeBytes {
        doc_id: u64,
        bytes: Vec<u8>,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    CombineBytes {
        files_bytes: Vec<Vec<u8>>,
        title: String,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    GetFormFields {
        doc_id: u64,
        page_index: usize,
        reply: mpsc::SyncSender<Result<Vec<FormFieldInfo>, String>>,
    },
    SetFormField {
        doc_id: u64,
        page_index: usize,
        annot_index: usize,
        value: String,
        checked: bool,
        reply: mpsc::SyncSender<Result<(), String>>,
    },
    Undo {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    Redo {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<DocumentOpenInfo, String>>,
    },
    GetUndoState {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<(bool, bool), String>>,
    },
    CreatePdfFromDocx {
        doc: DocxDocument,
        open_in_engine: bool,
        reply: mpsc::SyncSender<Result<(Option<DocumentOpenInfo>, Vec<u8>), String>>,
    },
    SaveBytes {
        doc_id: u64,
        reply: mpsc::SyncSender<Result<Vec<u8>, String>>,
    },
}

fn bind_pdfium(hint_dir: Option<&str>) -> Result<Pdfium, String> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Some(dir) = hint_dir {
        candidates.push(Path::new(dir).to_path_buf());
    }
    if let Ok(env_dir) = std::env::var("VNYL_NATIVE_DIR") {
        candidates.push(PathBuf::from(env_dir));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(parent) = exe.parent() {
            candidates.push(parent.to_path_buf());
        }
    }
    candidates.push(PathBuf::from("native/win-x64"));
    candidates.push(PathBuf::from("../../native/win-x64"));
    candidates.push(PathBuf::from("."));

    let mut last_err = String::new();
    for dir in candidates {
        let lib_path = Pdfium::pdfium_platform_library_name_at_path(&dir);
        if lib_path.exists() {
            match Pdfium::bind_to_library(&lib_path) {
                Ok(bindings) => return Ok(Pdfium::new(bindings)),
                Err(e) => last_err = format!("{e:?}"),
            }
        }
    }

    Pdfium::bind_to_system_library()
        .map(Pdfium::new)
        .map_err(|e| {
            format!(
                "Failed to load PDFium library (tried hint dir and system library): {last_err}; system error: {e:?}"
            )
        })
}

/// Initialize the PDFium worker thread once (serialized via `OnceCell::get_or_try_init`).
pub fn init_pdfium(hint_dir: Option<&str>) -> Result<(), String> {
    init_worker(hint_dir).map(|_| ())
}

fn init_worker(hint_dir: Option<&str>) -> Result<&'static mpsc::Sender<PdfCommand>, String> {
    WORKER_TX.get_or_try_init(|| {
        let hint_owned = hint_dir.map(|s| s.to_string());
        let (init_tx, init_rx) = mpsc::sync_channel::<Result<(), String>>(1);
        let (cmd_tx, cmd_rx) = mpsc::channel::<PdfCommand>();

        std::thread::Builder::new()
            .name("vnyl-pdfium".to_string())
            .spawn(move || {
                let pdfium = match bind_pdfium(hint_owned.as_deref()) {
                    Ok(p) => {
                        let _ = init_tx.send(Ok(()));
                        p
                    }
                    Err(e) => {
                        let _ = init_tx.send(Err(e));
                        return;
                    }
                };

                let pdfium: &'static Pdfium = Box::leak(Box::new(pdfium));
                let mut docs: HashMap<u64, (String, PdfDocument<'static>)> = HashMap::new();
                let mut history: HashMap<u64, (Vec<Vec<u8>>, Vec<Vec<u8>>)> = HashMap::new();

                while let Ok(cmd) = cmd_rx.recv() {
                    match cmd {
                        PdfCommand::OpenBytes {
                            bytes,
                            title_fallback,
                            password,
                            reply,
                        } => {
                            let has_pwd = password.as_deref().map(|s| !s.is_empty()).unwrap_or(false);
                            let res = pdfium
                                .load_pdf_from_byte_vec(bytes, password.as_deref())
                                .map_err(|e| {
                                    let detail = format!("{e:?}");
                                    if detail.contains("Password") {
                                        if has_pwd {
                                            "PASSWORD_INCORRECT: The password entered for this PDF is incorrect.".to_string()
                                        } else {
                                            "PASSWORD_REQUIRED: This PDF is encrypted and requires a password.".to_string()
                                        }
                                    } else {
                                        format!("Failed to open PDF '{title_fallback}': {detail}")
                                    }
                                })
                                .map(|doc| {
                                    let doc_id = NEXT_DOC_ID.fetch_add(1, Ordering::Relaxed);
                                    let title = doc
                                        .metadata()
                                        .get(PdfDocumentMetadataTagType::Title)
                                        .map(|t| t.value().trim().to_string())
                                        .filter(|t| !t.is_empty())
                                        .unwrap_or(title_fallback);
                                    let info = collect_document_info(doc_id, title.clone(), &doc);
                                    docs.insert(doc_id, (title, doc));
                                    info
                                });
                            let _ = reply.send(res);
                        }
                        PdfCommand::Close { doc_id, reply } => {
                            history.remove(&doc_id);
                            let _ = reply.send(docs.remove(&doc_id).is_some());
                        }
                        PdfCommand::RenderPage {
                            doc_id,
                            page_index,
                            width_px,
                            height_px,
                            reply,
                        } => {
                            let res = render_doc_page(
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                width_px,
                                height_px,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::RenderPageDpi {
                            doc_id,
                            page_index,
                            dpi,
                            reply,
                        } => {
                            let res = (|| -> Result<(u32, u32, Vec<u8>), String> {
                                let doc = docs
                                    .get(&doc_id)
                                    .map(|(_, d)| d)
                                    .ok_or_else(|| format!("Unknown document id {doc_id}"))?;
                                let page = doc
                                    .pages()
                                    .get(page_index as u16)
                                    .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
                                let scale = (dpi.clamp(72, 300) as f32) / 72.0;
                                let w = (page.width().value * scale).round().max(1.0) as i32;
                                let h = (page.height().value * scale).round().max(1.0) as i32;
                                let bytes = render_doc_page(Some(doc), doc_id, page_index, w, h)?;
                                Ok((w as u32, h as u32, bytes))
                            })();
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetOutline { doc_id, reply } => {
                            let res = extract_outline(docs.get(&doc_id).map(|(_, d)| d), doc_id);
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetMetadata { doc_id, reply } => {
                            let res = extract_metadata(docs.get(&doc_id), doc_id);
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetPageText {
                            doc_id,
                            page_index,
                            reply,
                        } => {
                            let res = extract_page_text(
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetPageTextSpans {
                            doc_id,
                            page_index,
                            reply,
                        } => {
                            let res = extract_page_text_spans(
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetPageLinks {
                            doc_id,
                            page_index,
                            reply,
                        } => {
                            let res = extract_page_links(
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetAnnotations { doc_id, reply } => {
                            let res = extract_annotations(docs.get(&doc_id).map(|(_, d)| d), doc_id);
                            let _ = reply.send(res);
                        }
                        PdfCommand::DeleteAnnotation {
                            doc_id,
                            page_index,
                            annot_index,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_delete_annotation(
                                docs.get_mut(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                annot_index,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::Search {
                            doc_id,
                            query,
                            max_hits,
                            reply,
                        } => {
                            let res = search_doc(
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                &query,
                                max_hits,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::AddHighlight {
                            doc_id,
                            page_index,
                            left,
                            top,
                            right,
                            bottom,
                            argb,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_highlight(
                                docs.get_mut(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                left,
                                top,
                                right,
                                bottom,
                                argb,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::AddInkStroke {
                            doc_id,
                            page_index,
                            points_norm,
                            argb,
                            stroke_width_pts,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_ink_stroke(
                                docs.get_mut(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                &points_norm,
                                argb,
                                stroke_width_pts,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::AddTextNote {
                            doc_id,
                            page_index,
                            norm_x,
                            norm_y,
                            text,
                            font_size_pts,
                            argb,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_text_note(
                                docs.get_mut(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                norm_x,
                                norm_y,
                                &text,
                                font_size_pts,
                                argb,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::AddImageBytes {
                            doc_id,
                            page_index,
                            image_bytes,
                            norm_x,
                            norm_y,
                            norm_width,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_image_bytes(
                                docs.get_mut(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                &image_bytes,
                                norm_x,
                                norm_y,
                                norm_width,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::RotatePage {
                            doc_id,
                            page_index,
                            delta_quarters,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_rotate_page(
                                docs.get_mut(&doc_id),
                                doc_id,
                                page_index,
                                delta_quarters,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::DeletePage {
                            doc_id,
                            page_index,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_delete_page(docs.get_mut(&doc_id), doc_id, page_index);
                            let _ = reply.send(res);
                        }
                        PdfCommand::ReorderPages {
                            doc_id,
                            order,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_reorder_pages(
                                pdfium,
                                docs.get_mut(&doc_id),
                                doc_id,
                                &order,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::ExtractPagesBytes {
                            doc_id,
                            pages_one_based,
                            reply,
                        } => {
                            let res = apply_extract_pages_bytes(
                                pdfium,
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                &pages_one_based,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::MergeBytes {
                            doc_id,
                            bytes,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_merge_bytes(pdfium, docs.get_mut(&doc_id), doc_id, bytes);
                            let _ = reply.send(res);
                        }
                        PdfCommand::CombineBytes {
                            files_bytes,
                            title,
                            reply,
                        } => {
                            let res = apply_combine_bytes(pdfium, &mut docs, files_bytes, title);
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetFormFields {
                            doc_id,
                            page_index,
                            reply,
                        } => {
                            let res = extract_form_fields(
                                docs.get(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::SetFormField {
                            doc_id,
                            page_index,
                            annot_index,
                            value,
                            checked,
                            reply,
                        } => {
                            push_undo_snapshot(docs.get(&doc_id), &mut history, doc_id);
                            let res = apply_set_form_field(
                                docs.get_mut(&doc_id).map(|(_, d)| d),
                                doc_id,
                                page_index,
                                annot_index,
                                &value,
                                checked,
                            );
                            let _ = reply.send(res);
                        }
                        PdfCommand::Undo { doc_id, reply } => {
                            let res = apply_undo_redo(pdfium, docs.get_mut(&doc_id), &mut history, doc_id, true);
                            let _ = reply.send(res);
                        }
                        PdfCommand::Redo { doc_id, reply } => {
                            let res = apply_undo_redo(pdfium, docs.get_mut(&doc_id), &mut history, doc_id, false);
                            let _ = reply.send(res);
                        }
                        PdfCommand::GetUndoState { doc_id, reply } => {
                            let res = if docs.contains_key(&doc_id) {
                                let (u, r) = history
                                    .get(&doc_id)
                                    .map(|(us, rs)| (!us.is_empty(), !rs.is_empty()))
                                    .unwrap_or((false, false));
                                Ok((u, r))
                            } else {
                                Err(format!("Unknown document id {doc_id}"))
                            };
                            let _ = reply.send(res);
                        }
                        PdfCommand::CreatePdfFromDocx {
                            doc: docx,
                            open_in_engine,
                            reply,
                        } => {
                            let res = build_pdf_from_docx(pdfium, &mut docs, &docx, open_in_engine);
                            let _ = reply.send(res);
                        }
                        PdfCommand::SaveBytes { doc_id, reply } => {
                            let res = match docs.get(&doc_id) {
                                Some((_, doc)) => doc
                                    .save_to_bytes()
                                    .map_err(|e| format!("Failed to serialize PDF: {e:?}")),
                                None => Err(format!("Unknown document id {doc_id}")),
                            };
                            let _ = reply.send(res);
                        }
                    }
                }
            })
            .map_err(|e| format!("Failed to spawn PDFium worker thread: {e}"))?;

        init_rx
            .recv()
            .map_err(|_| "PDFium worker exited during initialization".to_string())??;

        Ok(cmd_tx)
    })
}

fn worker() -> Result<&'static mpsc::Sender<PdfCommand>, String> {
    init_worker(None)
}

fn argb_to_pdf_color(argb: u32) -> PdfColor {
    let a = ((argb >> 24) & 0xFF) as u8;
    let r = ((argb >> 16) & 0xFF) as u8;
    let g = ((argb >> 8) & 0xFF) as u8;
    let b = (argb & 0xFF) as u8;
    PdfColor::new(r, g, b, a)
}

fn norm_rect_to_pdf(page: &PdfPage<'_>, left: f32, top: f32, right: f32, bottom: f32) -> PdfRect {
    let w = page.width().value;
    let h = page.height().value;
    let pdf_left = (left.min(right).clamp(0.0, 1.0)) * w;
    let pdf_right = (left.max(right).clamp(0.0, 1.0)) * w;
    let pdf_top = (1.0 - top.min(bottom).clamp(0.0, 1.0)) * h;
    let pdf_bottom = (1.0 - top.max(bottom).clamp(0.0, 1.0)) * h;
    PdfRect::new_from_values(pdf_bottom, pdf_left, pdf_top, pdf_right)
}

fn pdf_rect_to_norm(page_w: f32, page_h: f32, b: PdfRect) -> (f32, f32, f32, f32) {
    let l = (b.left().value / page_w).clamp(0.0, 1.0);
    let r = (b.right().value / page_w).clamp(0.0, 1.0);
    let t = (1.0 - (b.top().value / page_h)).clamp(0.0, 1.0);
    let bt = (1.0 - (b.bottom().value / page_h)).clamp(0.0, 1.0);
    (l.min(r), t.min(bt), l.max(r), t.max(bt))
}

fn collect_document_info(doc_id: u64, title: String, doc: &PdfDocument<'_>) -> DocumentOpenInfo {
    let page_count = doc.pages().len() as usize;
    let mut pages = Vec::with_capacity(page_count);
    for page in doc.pages().iter() {
        pages.push(PageSizeInfo {
            width_pts: page.width().value,
            height_pts: page.height().value,
        });
    }
    DocumentOpenInfo {
        doc_id,
        title,
        page_count,
        pages,
    }
}

fn render_doc_page(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    width_px: i32,
    height_px: i32,
) -> Result<Vec<u8>, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;

    let config = PdfRenderConfig::new()
        .set_fixed_size(width_px, height_px)
        .set_format(PdfBitmapFormat::BGRA)
        .render_form_data(true)
        .render_annotations(true);

    let bitmap = page
        .render_with_config(&config)
        .map_err(|e| format!("Render error on page {page_index}: {e:?}"))?;
    Ok(bitmap.as_raw_bytes())
}

fn extract_metadata(
    entry: Option<&(String, PdfDocument<'static>)>,
    doc_id: u64,
) -> Result<DocumentMetadataInfo, String> {
    let (fallback_title, doc) = entry.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let meta = doc.metadata();
    let get_tag = |tag: PdfDocumentMetadataTagType| -> String {
        meta.get(tag)
            .map(|t| t.value().trim().to_string())
            .unwrap_or_default()
    };
    let title = get_tag(PdfDocumentMetadataTagType::Title);
    Ok(DocumentMetadataInfo {
        title: if title.is_empty() { fallback_title.clone() } else { title },
        author: get_tag(PdfDocumentMetadataTagType::Author),
        subject: get_tag(PdfDocumentMetadataTagType::Subject),
        creator: get_tag(PdfDocumentMetadataTagType::Creator),
        producer: get_tag(PdfDocumentMetadataTagType::Producer),
        page_count: doc.pages().len() as usize,
    })
}

fn extract_outline(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
) -> Result<Vec<OutlineItemInfo>, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;

    fn walk<'a>(bookmark: PdfBookmark<'a>) -> OutlineItemInfo {
        let title = bookmark.title().unwrap_or_default();
        let page_index = bookmark
            .destination()
            .and_then(|d| d.page_index().ok())
            .map(|idx| idx as usize);
        let mut children = Vec::new();
        let mut child_opt = bookmark.first_child();
        while let Some(child) = child_opt {
            let next = child.next_sibling();
            children.push(walk(child));
            child_opt = next;
        }
        OutlineItemInfo {
            title,
            page_index,
            children,
        }
    }

    let mut roots = Vec::new();
    if let Some(root) = doc.bookmarks().root() {
        let mut current = Some(root);
        while let Some(bm) = current {
            let next = bm.next_sibling();
            roots.push(walk(bm));
            current = next;
        }
    }
    Ok(roots)
}

fn extract_page_text(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
) -> Result<String, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let text = page
        .text()
        .map_err(|e| format!("Failed to read page text: {e:?}"))?;
    Ok(text.all())
}

fn extract_page_text_spans(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
) -> Result<Vec<TextSpanInfo>, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let page_w = page.width().value.max(1.0);
    let page_h = page.height().value.max(1.0);
    let text = page
        .text()
        .map_err(|e| format!("Failed to read page text: {e:?}"))?;

    let mut spans = Vec::new();
    for seg in text.segments().iter() {
        let s = seg.text();
        if s.trim().is_empty() {
            continue;
        }
        let (left, top, right, bottom) = pdf_rect_to_norm(page_w, page_h, seg.bounds());
        spans.push(TextSpanInfo {
            text: s,
            left,
            top,
            right,
            bottom,
        });
    }
    Ok(spans)
}

fn extract_page_links(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
) -> Result<Vec<PageLinkInfo>, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let page_w = page.width().value.max(1.0);
    let page_h = page.height().value.max(1.0);

    let mut out = Vec::new();
    for link in page.links().iter() {
        let Ok(rect) = link.rect() else { continue };
        let (left, top, right, bottom) = pdf_rect_to_norm(page_w, page_h, rect);
        let mut uri: Option<String> = None;
        let mut dest_page: Option<usize> = link
            .destination()
            .and_then(|d| d.page_index().ok())
            .map(|p| p as usize);

        if let Some(action) = link.action() {
            match action {
                PdfAction::Uri(u) => {
                    uri = u.uri().ok();
                }
                PdfAction::LocalDestination(local) => {
                    if dest_page.is_none() {
                        dest_page = local
                            .destination()
                            .ok()
                            .and_then(|d| d.page_index().ok())
                            .map(|p| p as usize);
                    }
                }
                _ => {}
            }
        }

        if uri.is_some() || dest_page.is_some() {
            out.push(PageLinkInfo {
                left,
                top,
                right,
                bottom,
                uri,
                dest_page,
            });
        }
    }
    Ok(out)
}

fn extract_annotations(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
) -> Result<Vec<AnnotationItemInfo>, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let mut items = Vec::new();
    for (page_index, page) in doc.pages().iter().enumerate() {
        let page_w = page.width().value.max(1.0);
        let page_h = page.height().value.max(1.0);
        for (annot_index, annot) in page.annotations().iter().enumerate() {
            let kind = format!("{:?}", annot.annotation_type());
            if kind == "Link" {
                continue;
            }
            let contents = annot.contents().unwrap_or_default();
            let (left, top, right, bottom) = annot
                .bounds()
                .map(|b| pdf_rect_to_norm(page_w, page_h, b))
                .unwrap_or((0.0, 0.0, 0.0, 0.0));
            items.push(AnnotationItemInfo {
                page_index,
                annot_index,
                kind,
                contents,
                left,
                top,
                right,
                bottom,
            });
        }
    }
    Ok(items)
}

fn apply_delete_annotation(
    doc: Option<&mut PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    annot_index: usize,
) -> Result<(), String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let mut page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let annot = page
        .annotations()
        .get(annot_index)
        .map_err(|e| format!("Annotation not found: {e:?}"))?;
    page.annotations_mut()
        .delete_annotation(annot)
        .map_err(|e| format!("Failed to delete annotation: {e:?}"))?;
    Ok(())
}

fn search_doc(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
    query: &str,
    max_hits: usize,
) -> Result<Vec<SearchHitInfo>, String> {
    let q = query.trim();
    if q.is_empty() {
        return Ok(Vec::new());
    }
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;

    let mut hits = Vec::new();
    let options = PdfSearchOptions::new();

    for (page_index, page) in doc.pages().iter().enumerate() {
        let page_w = page.width().value.max(1.0);
        let page_h = page.height().value.max(1.0);
        let Ok(page_text) = page.text() else {
            continue;
        };
        let Ok(search) = page_text.search(q, &options) else {
            continue;
        };

        for segments in search.iter(PdfSearchDirection::SearchForward) {
            for seg in segments.iter() {
                let (left, top, right, bottom) = pdf_rect_to_norm(page_w, page_h, seg.bounds());
                hits.push(SearchHitInfo {
                    page_index,
                    match_text: seg.text(),
                    left,
                    top,
                    right,
                    bottom,
                });
                if hits.len() >= max_hits {
                    return Ok(hits);
                }
            }
        }
    }

    Ok(hits)
}

#[allow(clippy::too_many_arguments)]
fn apply_highlight(
    doc: Option<&mut PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    left: f32,
    top: f32,
    right: f32,
    bottom: f32,
    argb: u32,
) -> Result<(), String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let mut page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let rect = norm_rect_to_pdf(&page, left, top, right, bottom);
    let color = argb_to_pdf_color(argb);

    let mut annot = page
        .annotations_mut()
        .create_highlight_annotation()
        .map_err(|e| format!("Failed to create highlight annotation: {e:?}"))?;
    annot
        .set_bounds(rect)
        .map_err(|e| format!("Failed to set highlight bounds: {e:?}"))?;
    annot
        .attachment_points_mut()
        .create_attachment_point_at_end(PdfQuadPoints::from_rect(&rect))
        .map_err(|e| format!("Failed to attach highlight quad: {e:?}"))?;
    annot
        .set_fill_color(color)
        .map_err(|e| format!("Failed to set highlight color: {e:?}"))?;
    Ok(())
}

fn apply_ink_stroke(
    doc: Option<&mut PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    points_norm: &[(f32, f32)],
    argb: u32,
    stroke_width_pts: f32,
) -> Result<(), String> {
    if points_norm.len() < 2 {
        return Ok(());
    }
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let color = argb_to_pdf_color(argb);

    let (page_w, page_h) = {
        let page = doc
            .pages()
            .get(page_index as u16)
            .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
        (page.width().value, page.height().value)
    };

    let (x0, y0) = points_norm[0];
    let start_x = PdfPoints::new(x0.clamp(0.0, 1.0) * page_w);
    let start_y = PdfPoints::new((1.0 - y0.clamp(0.0, 1.0)) * page_h);

    let mut path = PdfPagePathObject::new(
        doc,
        start_x,
        start_y,
        Some(color),
        Some(PdfPoints::new(stroke_width_pts.max(0.5))),
        None,
    )
    .map_err(|e| format!("Failed to create ink path: {e:?}"))?;

    for &(nx, ny) in &points_norm[1..] {
        let px = PdfPoints::new(nx.clamp(0.0, 1.0) * page_w);
        let py = PdfPoints::new((1.0 - ny.clamp(0.0, 1.0)) * page_h);
        path.line_to(px, py)
            .map_err(|e| format!("Failed to append ink vertex: {e:?}"))?;
    }

    let mut page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    page.objects_mut()
        .add_path_object(path)
        .map_err(|e| format!("Failed to attach ink stroke: {e:?}"))?;
    page.regenerate_content()
        .map_err(|e| format!("Failed to regenerate page content: {e:?}"))?;
    Ok(())
}

#[allow(clippy::too_many_arguments)]
fn apply_text_note(
    doc: Option<&mut PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    norm_x: f32,
    norm_y: f32,
    text: &str,
    font_size_pts: f32,
    argb: u32,
) -> Result<(), String> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return Ok(());
    }
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let font = doc.fonts_mut().helvetica();
    let color = argb_to_pdf_color(argb);

    let mut page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let x = PdfPoints::new(norm_x.clamp(0.0, 1.0) * page.width().value);
    let y = PdfPoints::new((1.0 - norm_y.clamp(0.0, 1.0)) * page.height().value);

    let mut obj = page
        .objects_mut()
        .create_text_object(x, y, trimmed, font, PdfPoints::new(font_size_pts.clamp(8.0, 72.0)))
        .map_err(|e| format!("Failed to create text object: {e:?}"))?;
    obj.set_fill_color(color)
        .map_err(|e| format!("Failed to set text color: {e:?}"))?;
    page.regenerate_content()
        .map_err(|e| format!("Failed to regenerate page content: {e:?}"))?;
    Ok(())
}

fn apply_image_bytes(
    doc: Option<&mut PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    image_bytes: &[u8],
    norm_x: f32,
    norm_y: f32,
    norm_width: f32,
) -> Result<(), String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let dyn_img = image::load_from_memory(image_bytes)
        .map_err(|e| format!("Failed to decode image bytes: {e}"))?;
    let (page_w, page_h) = {
        let page = doc
            .pages()
            .get(page_index as u16)
            .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
        (page.width().value, page.height().value)
    };

    let target_w_pts = (norm_width.clamp(0.04, 0.95) * page_w).max(16.0);
    let mut img_obj = PdfPageImageObject::new_with_width(doc, &dyn_img, PdfPoints::new(target_w_pts))
        .map_err(|e| format!("Failed to create PDF image object: {e:?}"))?;
    let img_h_pts = target_w_pts * (dyn_img.height() as f32 / dyn_img.width().max(1) as f32);

    let x_pts = norm_x.clamp(0.0, 0.95) * page_w;
    let y_pts = ((1.0 - norm_y.clamp(0.0, 0.95)) * page_h - img_h_pts).max(0.0);
    img_obj
        .translate(PdfPoints::new(x_pts), PdfPoints::new(y_pts))
        .map_err(|e| format!("Failed to position PDF image: {e:?}"))?;

    let mut page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    page.objects_mut()
        .add_image_object(img_obj)
        .map_err(|e| format!("Failed to attach image to page: {e:?}"))?;
    page.regenerate_content()
        .map_err(|e| format!("Failed to regenerate page content: {e:?}"))?;
    Ok(())
}

fn apply_rotate_page(
    entry: Option<&mut (String, PdfDocument<'static>)>,
    doc_id: u64,
    page_index: usize,
    delta_quarters: i32,
) -> Result<DocumentOpenInfo, String> {
    let (title, doc) = entry.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    {
        let mut page = doc
            .pages()
            .get(page_index as u16)
            .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
        let current = match page.rotation().unwrap_or(PdfPageRenderRotation::None) {
            PdfPageRenderRotation::None => 0,
            PdfPageRenderRotation::Degrees90 => 1,
            PdfPageRenderRotation::Degrees180 => 2,
            PdfPageRenderRotation::Degrees270 => 3,
        };
        let next = (current + delta_quarters).rem_euclid(4);
        let rot = match next {
            1 => PdfPageRenderRotation::Degrees90,
            2 => PdfPageRenderRotation::Degrees180,
            3 => PdfPageRenderRotation::Degrees270,
            _ => PdfPageRenderRotation::None,
        };
        page.set_rotation(rot);
    }
    Ok(collect_document_info(doc_id, title.clone(), doc))
}

fn apply_delete_page(
    entry: Option<&mut (String, PdfDocument<'static>)>,
    doc_id: u64,
    page_index: usize,
) -> Result<DocumentOpenInfo, String> {
    let (title, doc) = entry.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    if doc.pages().len() <= 1 {
        return Err("Cannot delete the last remaining page".to_string());
    }
    #[allow(deprecated)]
    doc.pages_mut()
        .delete_page_at_index(page_index as u16)
        .map_err(|e| format!("Failed to delete page {page_index}: {e:?}"))?;
    Ok(collect_document_info(doc_id, title.clone(), doc))
}

fn apply_reorder_pages(
    pdfium: &'static Pdfium,
    entry: Option<&mut (String, PdfDocument<'static>)>,
    doc_id: u64,
    order: &[usize],
) -> Result<DocumentOpenInfo, String> {
    let (title, old_doc) = entry.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    if order.is_empty() {
        return Err("Page order cannot be empty".to_string());
    }
    let page_len = old_doc.pages().len() as usize;
    let mut new_doc = pdfium
        .create_new_pdf()
        .map_err(|e| format!("Failed to create reordered PDF: {e:?}"))?;
    for (dest_idx, &src_idx) in order.iter().enumerate() {
        if src_idx >= page_len {
            return Err(format!("Page index {src_idx} out of bounds"));
        }
        new_doc
            .pages_mut()
            .copy_page_from_document(old_doc, src_idx as u16, dest_idx as u16)
            .map_err(|e| format!("Failed copying page {src_idx}: {e:?}"))?;
    }
    *old_doc = new_doc;
    Ok(collect_document_info(doc_id, title.clone(), old_doc))
}

fn apply_extract_pages_bytes(
    pdfium: &'static Pdfium,
    doc: Option<&PdfDocument<'static>>,
    doc_id: u64,
    pages_one_based: &str,
) -> Result<Vec<u8>, String> {
    let src = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let mut out = pdfium
        .create_new_pdf()
        .map_err(|e| format!("Failed to create extracted PDF: {e:?}"))?;
    out.pages_mut()
        .copy_pages_from_document(src, pages_one_based, 0)
        .map_err(|e| format!("Invalid page range '{pages_one_based}': {e:?}"))?;
    out.save_to_bytes()
        .map_err(|e| format!("Failed to serialize extracted PDF: {e:?}"))
}

fn apply_merge_bytes(
    pdfium: &'static Pdfium,
    entry: Option<&mut (String, PdfDocument<'static>)>,
    doc_id: u64,
    bytes: Vec<u8>,
) -> Result<DocumentOpenInfo, String> {
    let (title, doc) = entry.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let src = pdfium
        .load_pdf_from_byte_vec(bytes, None)
        .map_err(|e| format!("Failed to open source PDF to merge: {e:?}"))?;
    let src_len = src.pages().len();
    if src_len == 0 {
        return Ok(collect_document_info(doc_id, title.clone(), doc));
    }
    let range_spec = format!("1-{src_len}");
    let dest_idx = doc.pages().len();
    doc.pages_mut()
        .copy_pages_from_document(&src, &range_spec, dest_idx)
        .map_err(|e| format!("Failed to merge PDF pages: {e:?}"))?;
    Ok(collect_document_info(doc_id, title.clone(), doc))
}

pub fn open_document_file(path: &str, password: Option<&str>) -> Result<DocumentOpenInfo, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("Failed to read '{path}': {e}"))?;
    let fallback_name = Path::new(path)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("Document.pdf");
    open_document_bytes(bytes, fallback_name, password)
}

pub fn open_document_bytes(
    bytes: Vec<u8>,
    name: &str,
    password: Option<&str>,
) -> Result<DocumentOpenInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::OpenBytes {
        bytes,
        title_fallback: name.to_string(),
        password: password.map(|s| s.to_string()),
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn close_document(doc_id: u64) -> bool {
    let Ok(tx) = worker() else {
        return false;
    };
    let (reply, rx) = mpsc::sync_channel(1);
    if tx.send(PdfCommand::Close { doc_id, reply }).is_err() {
        return false;
    }
    rx.recv().unwrap_or(false)
}

pub fn render_page_into_bgra(
    doc_id: u64,
    page_index: usize,
    width_px: i32,
    height_px: i32,
    out_bgra: &mut [u8],
) -> Result<(), String> {
    if width_px <= 0 || height_px <= 0 {
        return Err("Invalid target dimensions".to_string());
    }
    let expected_len = (width_px as usize)
        .checked_mul(height_px as usize)
        .and_then(|px| px.checked_mul(4))
        .ok_or_else(|| "Target pixel buffer size overflow".to_string())?;
    if out_bgra.len() < expected_len {
        return Err(format!(
            "Output buffer too small: got {}, need {expected_len}",
            out_bgra.len()
        ));
    }

    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::RenderPage {
        doc_id,
        page_index,
        width_px,
        height_px,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    let raw = rx
        .recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())??;
    let copy_len = expected_len.min(raw.len());
    out_bgra[..copy_len].copy_from_slice(&raw[..copy_len]);
    Ok(())
}

pub fn get_outline(doc_id: u64) -> Result<Vec<OutlineItemInfo>, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetOutline { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_metadata(doc_id: u64) -> Result<DocumentMetadataInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetMetadata { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_page_text(doc_id: u64, page_index: usize) -> Result<String, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetPageText {
        doc_id,
        page_index,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_page_text_spans(doc_id: u64, page_index: usize) -> Result<Vec<TextSpanInfo>, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetPageTextSpans {
        doc_id,
        page_index,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_page_links(doc_id: u64, page_index: usize) -> Result<Vec<PageLinkInfo>, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetPageLinks {
        doc_id,
        page_index,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_annotations(doc_id: u64) -> Result<Vec<AnnotationItemInfo>, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetAnnotations { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn delete_annotation(doc_id: u64, page_index: usize, annot_index: usize) -> Result<(), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::DeleteAnnotation {
        doc_id,
        page_index,
        annot_index,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn search_document(
    doc_id: u64,
    query: &str,
    max_hits: usize,
) -> Result<Vec<SearchHitInfo>, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::Search {
        doc_id,
        query: query.to_string(),
        max_hits,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn add_highlight(
    doc_id: u64,
    page_index: usize,
    left: f32,
    top: f32,
    right: f32,
    bottom: f32,
    argb: u32,
) -> Result<(), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::AddHighlight {
        doc_id,
        page_index,
        left,
        top,
        right,
        bottom,
        argb,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn add_ink_stroke(
    doc_id: u64,
    page_index: usize,
    points_norm: Vec<(f32, f32)>,
    argb: u32,
    stroke_width_pts: f32,
) -> Result<(), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::AddInkStroke {
        doc_id,
        page_index,
        points_norm,
        argb,
        stroke_width_pts,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn add_text_note(
    doc_id: u64,
    page_index: usize,
    norm_x: f32,
    norm_y: f32,
    text: String,
    font_size_pts: f32,
    argb: u32,
) -> Result<(), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::AddTextNote {
        doc_id,
        page_index,
        norm_x,
        norm_y,
        text,
        font_size_pts,
        argb,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn add_image_bytes(
    doc_id: u64,
    page_index: usize,
    image_bytes: Vec<u8>,
    norm_x: f32,
    norm_y: f32,
    norm_width: f32,
) -> Result<(), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::AddImageBytes {
        doc_id,
        page_index,
        image_bytes,
        norm_x,
        norm_y,
        norm_width,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn rotate_page(
    doc_id: u64,
    page_index: usize,
    delta_quarters: i32,
) -> Result<DocumentOpenInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::RotatePage {
        doc_id,
        page_index,
        delta_quarters,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn delete_page(doc_id: u64, page_index: usize) -> Result<DocumentOpenInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::DeletePage {
        doc_id,
        page_index,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn reorder_pages(doc_id: u64, order: Vec<usize>) -> Result<DocumentOpenInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::ReorderPages {
        doc_id,
        order,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn extract_pages_to_file(
    doc_id: u64,
    pages_one_based: &str,
    dest_path: &str,
) -> Result<(), String> {
    if !security::has_allowed_extension(dest_path, &["pdf"]) {
        return Err(format!("Unsupported file type (expected .pdf): {dest_path}"));
    }
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::ExtractPagesBytes {
        doc_id,
        pages_one_based: pages_one_based.to_string(),
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    let bytes = rx
        .recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())??;
    security::write_atomic(dest_path, &bytes)
}

pub fn split_document(
    doc_id: u64,
    page_count: usize,
    chunk_size: usize,
    out_dir: &str,
    base_name: &str,
) -> Result<usize, String> {
    let step = chunk_size.max(1);
    let dir = Path::new(out_dir);
    std::fs::create_dir_all(dir).map_err(|e| format!("Failed creating output folder: {e}"))?;

    let mut written = 0usize;
    let mut start = 1usize;
    while start <= page_count {
        let end = (start + step - 1).min(page_count);
        let range = if start == end {
            format!("{start}")
        } else {
            format!("{start}-{end}")
        };
        let filename = format!("{base_name}_pages_{range}.pdf");
        let dest = dir.join(filename);
        extract_pages_to_file(doc_id, &range, &dest.to_string_lossy())?;
        written += 1;
        start = end + 1;
    }
    Ok(written)
}

pub fn merge_pdf_file(doc_id: u64, source_path: &str) -> Result<DocumentOpenInfo, String> {
    let bytes =
        std::fs::read(source_path).map_err(|e| format!("Failed to read '{source_path}': {e}"))?;
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::MergeBytes {
        doc_id,
        bytes,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn save_document(doc_id: u64, dest_path: &str) -> Result<(), String> {
    if !security::has_allowed_extension(dest_path, &["pdf"]) {
        return Err(format!("Unsupported file type (expected .pdf): {dest_path}"));
    }
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::SaveBytes { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    let bytes = rx
        .recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())??;
    security::write_atomic(dest_path, &bytes)
}

pub fn export_page_image(
    doc_id: u64,
    page_index: usize,
    dest_path: &str,
    dpi: u32,
) -> Result<(), String> {
    if !security::has_allowed_extension(dest_path, &["png", "jpg", "jpeg"]) {
        return Err(format!(
            "Unsupported image export extension (expected png/jpg): {dest_path}"
        ));
    }
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::RenderPageDpi {
        doc_id,
        page_index,
        dpi,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    let (width_px, height_px, mut bgra) = rx
        .recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())??;

    for chunk in bgra.chunks_exact_mut(4) {
        chunk.swap(0, 2);
    }

    let img = image::RgbaImage::from_raw(width_px, height_px, bgra)
        .ok_or_else(|| "Rendered image dimensions mismatch".to_string())?;
    let mut encoded = Vec::new();
    let format = if dest_path.to_ascii_lowercase().ends_with(".png") {
        image::ImageFormat::Png
    } else {
        image::ImageFormat::Jpeg
    };
    let dyn_img = if format == image::ImageFormat::Jpeg {
        image::DynamicImage::ImageRgba8(img).to_rgb8().into()
    } else {
        image::DynamicImage::ImageRgba8(img)
    };
    dyn_img
        .write_to(&mut Cursor::new(&mut encoded), format)
        .map_err(|e| format!("Failed to encode image: {e}"))?;
    security::write_atomic(dest_path, &encoded)
}

pub fn export_document_text(
    doc_id: u64,
    page_count: usize,
    title: &str,
    dest_path: &str,
    as_markdown: bool,
) -> Result<(), String> {
    if !security::has_allowed_extension(dest_path, &["txt", "md"]) {
        return Err(format!(
            "Unsupported text export extension (expected txt/md): {dest_path}"
        ));
    }
    let mut out = String::new();
    if as_markdown {
        out.push_str(&format!("# {title}\n\n"));
    }
    for idx in 0..page_count {
        let page_text = get_page_text(doc_id, idx)?;
        if as_markdown {
            out.push_str(&format!("## Page {}\n\n{}\n\n", idx + 1, page_text.trim()));
        } else {
            out.push_str(&format!("--- Page {} ---\n{}\n\n", idx + 1, page_text.trim()));
        }
    }
    security::write_atomic(dest_path, out.as_bytes())
}

fn push_undo_snapshot(
    entry: Option<&(String, PdfDocument<'static>)>,
    history: &mut HashMap<u64, (Vec<Vec<u8>>, Vec<Vec<u8>>)>,
    doc_id: u64,
) {
    let Some((_, doc)) = entry else { return };
    if let Ok(bytes) = doc.save_to_bytes() {
        let (undo_stack, redo_stack) = history.entry(doc_id).or_default();
        if undo_stack.len() >= 12 {
            undo_stack.remove(0);
        }
        undo_stack.push(bytes);
        redo_stack.clear();
    }
}

fn apply_undo_redo(
    pdfium: &'static Pdfium,
    entry: Option<&mut (String, PdfDocument<'static>)>,
    history: &mut HashMap<u64, (Vec<Vec<u8>>, Vec<Vec<u8>>)>,
    doc_id: u64,
    is_undo: bool,
) -> Result<DocumentOpenInfo, String> {
    let (title, doc) = entry.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let (undo_stack, redo_stack) = history.entry(doc_id).or_default();
    let target_bytes = if is_undo {
        undo_stack.pop().ok_or_else(|| "Nothing to undo".to_string())?
    } else {
        redo_stack.pop().ok_or_else(|| "Nothing to redo".to_string())?
    };

    let current_bytes = doc
        .save_to_bytes()
        .map_err(|e| format!("Failed snapshotting document: {e:?}"))?;
    let restored = pdfium
        .load_pdf_from_byte_vec(target_bytes, None)
        .map_err(|e| format!("Failed restoring snapshot: {e:?}"))?;
    if is_undo {
        if redo_stack.len() >= 12 {
            redo_stack.remove(0);
        }
        redo_stack.push(current_bytes);
    } else {
        if undo_stack.len() >= 12 {
            undo_stack.remove(0);
        }
        undo_stack.push(current_bytes);
    }
    *doc = restored;
    Ok(collect_document_info(doc_id, title.clone(), doc))
}

fn apply_combine_bytes(
    pdfium: &'static Pdfium,
    docs: &mut HashMap<u64, (String, PdfDocument<'static>)>,
    files_bytes: Vec<Vec<u8>>,
    title: String,
) -> Result<DocumentOpenInfo, String> {
    if files_bytes.is_empty() {
        return Err("No PDF files provided to combine".to_string());
    }
    let mut combined = pdfium
        .create_new_pdf()
        .map_err(|e| format!("Failed creating combined PDF: {e:?}"))?;
    for bytes in files_bytes {
        let src = pdfium
            .load_pdf_from_byte_vec(bytes, None)
            .map_err(|e| format!("Failed loading PDF to combine: {e:?}"))?;
        let count = src.pages().len();
        if count > 0 {
            let dest = combined.pages().len();
            combined
                .pages_mut()
                .copy_pages_from_document(&src, &format!("1-{count}"), dest)
                .map_err(|e| format!("Failed combining PDF pages: {e:?}"))?;
        }
    }
    let doc_id = NEXT_DOC_ID.fetch_add(1, Ordering::Relaxed);
    let info = collect_document_info(doc_id, title.clone(), &combined);
    docs.insert(doc_id, (title, combined));
    Ok(info)
}

fn extract_form_fields(
    doc: Option<&PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
) -> Result<Vec<FormFieldInfo>, String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let page_w = page.width().value.max(1.0);
    let page_h = page.height().value.max(1.0);

    let mut out = Vec::new();
    for (annot_index, annot) in page.annotations().iter().enumerate() {
        let bounds = annot
            .bounds()
            .map(|b| pdf_rect_to_norm(page_w, page_h, b))
            .unwrap_or((0.0, 0.0, 0.0, 0.0));
        if let PdfPageAnnotation::Widget(ref w) = annot {
            if let Some(field) = w.form_field() {
                let (kind, name, value, checked) = match field {
                    PdfFormField::Text(f) => (
                        "Text".to_string(),
                        f.name().unwrap_or_default(),
                        f.value().unwrap_or_default(),
                        false,
                    ),
                    PdfFormField::Checkbox(f) => (
                        "Checkbox".to_string(),
                        f.name().unwrap_or_default(),
                        String::new(),
                        f.is_checked().unwrap_or(false),
                    ),
                    PdfFormField::RadioButton(f) => (
                        "RadioButton".to_string(),
                        f.name().unwrap_or_default(),
                        String::new(),
                        f.is_checked().unwrap_or(false),
                    ),
                    PdfFormField::ComboBox(f) => (
                        "ComboBox".to_string(),
                        f.name().unwrap_or_default(),
                        f.value().unwrap_or_default(),
                        false,
                    ),
                    PdfFormField::ListBox(f) => (
                        "ListBox".to_string(),
                        f.name().unwrap_or_default(),
                        f.value().unwrap_or_default(),
                        false,
                    ),
                    PdfFormField::Signature(f) => (
                        "Signature".to_string(),
                        f.name().unwrap_or_default(),
                        String::new(),
                        false,
                    ),
                    _ => continue,
                };
                out.push(FormFieldInfo {
                    page_index,
                    annot_index,
                    kind,
                    name,
                    value,
                    checked,
                    left: bounds.0,
                    top: bounds.1,
                    right: bounds.2,
                    bottom: bounds.3,
                });
            }
        }
    }
    Ok(out)
}

fn apply_set_form_field(
    doc: Option<&mut PdfDocument<'_>>,
    doc_id: u64,
    page_index: usize,
    annot_index: usize,
    value: &str,
    checked: bool,
) -> Result<(), String> {
    let doc = doc.ok_or_else(|| format!("Unknown document id {doc_id}"))?;
    let page = doc
        .pages()
        .get(page_index as u16)
        .map_err(|e| format!("Failed to get page {page_index}: {e:?}"))?;
    let mut annot = page
        .annotations()
        .get(annot_index)
        .map_err(|e| format!("Form widget not found: {e:?}"))?;
    let PdfPageAnnotation::Widget(ref mut widget) = annot else {
        return Err("Annotation is not a form widget".to_string());
    };
    let Some(field) = widget.form_field_mut() else {
        return Err("Widget has no form field".to_string());
    };
    match field {
        PdfFormField::Text(f) => f
            .set_value(value)
            .map_err(|e| format!("Failed setting text field: {e:?}"))?,
        PdfFormField::Checkbox(f) => f
            .set_checked(checked)
            .map_err(|e| format!("Failed setting checkbox: {e:?}"))?,
        PdfFormField::RadioButton(f) => {
            if checked {
                f.set_checked()
                    .map_err(|e| format!("Failed setting radio button: {e:?}"))?;
            }
        }
        _ => return Err("Unsupported form field type for direct editing".to_string()),
    }
    Ok(())
}

pub fn undo_document(doc_id: u64) -> Result<DocumentOpenInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::Undo { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn redo_document(doc_id: u64) -> Result<DocumentOpenInfo, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::Redo { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn combine_pdf_files(paths: Vec<String>, title: &str) -> Result<DocumentOpenInfo, String> {
    let mut files_bytes = Vec::with_capacity(paths.len());
    for p in paths {
        let bytes = std::fs::read(&p).map_err(|e| format!("Failed reading '{p}': {e}"))?;
        files_bytes.push(bytes);
    }
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::CombineBytes {
        files_bytes,
        title: title.to_string(),
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_form_fields(doc_id: u64, page_index: usize) -> Result<Vec<FormFieldInfo>, String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetFormFields {
        doc_id,
        page_index,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn set_form_field(
    doc_id: u64,
    page_index: usize,
    annot_index: usize,
    value: &str,
    checked: bool,
) -> Result<(), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::SetFormField {
        doc_id,
        page_index,
        annot_index,
        value: value.to_string(),
        checked,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn get_undo_state(doc_id: u64) -> Result<(bool, bool), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::GetUndoState { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

fn wrap_words(text: &str, max_chars: usize) -> Vec<String> {
    let mut lines = Vec::new();
    for raw_line in text.split('\n') {
        let trimmed_end = raw_line.trim_end();
        if trimmed_end.is_empty() {
            lines.push(String::new());
            continue;
        }
        let mut current = String::new();
        for word in trimmed_end.split_whitespace() {
            if current.is_empty() {
                current.push_str(word);
            } else if current.len() + 1 + word.len() <= max_chars {
                current.push(' ');
                current.push_str(word);
            } else {
                lines.push(current);
                current = word.to_string();
            }
        }
        if !current.is_empty() {
            lines.push(current);
        }
    }
    if lines.is_empty() {
        lines.push(String::new());
    }
    lines
}

fn build_pdf_from_docx(
    pdfium: &'static Pdfium,
    docs: &mut HashMap<u64, (String, PdfDocument<'static>)>,
    docx: &DocxDocument,
    open_in_engine: bool,
) -> Result<(Option<DocumentOpenInfo>, Vec<u8>), String> {
    let mut pdf = pdfium
        .create_new_pdf()
        .map_err(|e| format!("Failed creating PDF document: {e:?}"))?;

    let font_regular = pdf.fonts_mut().helvetica();
    let font_bold = pdf.fonts_mut().helvetica_bold();
    let font_italic = pdf.fonts_mut().helvetica_oblique();

    let page_w = 612.0f32;
    let page_h = 792.0f32;
    let margin_x = 56.0f32;
    let margin_top = 60.0f32;
    let margin_bottom = 54.0f32;

    let mut page = pdf
        .pages_mut()
        .create_page_at_end(PdfPagePaperSize::from_inches(8.5, 11.0))
        .map_err(|e| format!("Failed adding PDF page: {e:?}"))?;
    let mut cursor_y = page_h - margin_top;
    let mut num_counter = 1usize;

    for block in &docx.blocks {
        if block.kind == "table" {
            num_counter = 1;
            let row_h = 22.0f32;
            for (r_idx, row) in block.rows.iter().enumerate() {
                if cursor_y - row_h < margin_bottom {
                    page.regenerate_content()
                        .map_err(|e| format!("Regenerate content error: {e:?}"))?;
                    page = pdf
                        .pages_mut()
                        .create_page_at_end(PdfPagePaperSize::from_inches(8.5, 11.0))
                        .map_err(|e| format!("Failed adding PDF page: {e:?}"))?;
                    cursor_y = page_h - margin_top;
                }
                let cols = row.len().max(1);
                let col_w = (page_w - margin_x * 2.0) / cols as f32;
                for (c_idx, cell) in row.iter().enumerate() {
                    let x = margin_x + c_idx as f32 * col_w + 4.0;
                    let font = if r_idx == 0 { font_bold } else { font_regular };
                    let text_trim: String = cell.chars().take(42).collect();
                    if !text_trim.is_empty() {
                        let mut obj = page
                            .objects_mut()
                            .create_text_object(
                                PdfPoints::new(x),
                                PdfPoints::new(cursor_y - 14.0),
                                &text_trim,
                                font,
                                PdfPoints::new(10.0),
                            )
                            .map_err(|e| format!("Table cell text failed: {e:?}"))?;
                        let _ = obj.set_fill_color(PdfColor::new(28, 30, 34, 255));
                    }
                }
                cursor_y -= row_h;
            }
            cursor_y -= 10.0;
            continue;
        }

        let (font_size, line_h, space_after, font, color) = match block.kind.as_str() {
            "h1" => {
                num_counter = 1;
                (20.0f32, 26.0f32, 10.0f32, font_bold, PdfColor::new(18, 19, 22, 255))
            }
            "h2" => {
                num_counter = 1;
                (15.5f32, 21.0f32, 8.0f32, font_bold, PdfColor::new(24, 26, 30, 255))
            }
            "h3" => {
                num_counter = 1;
                (12.5f32, 18.0f32, 6.0f32, font_bold, PdfColor::new(38, 40, 46, 255))
            }
            "quote" => {
                num_counter = 1;
                (11.0f32, 16.5f32, 8.0f32, font_italic, PdfColor::new(82, 86, 94, 255))
            }
            _ => {
                let any_bold = block.runs.iter().all(|r| r.bold) && !block.runs.is_empty();
                let any_italic = block.runs.iter().all(|r| r.italic) && !block.runs.is_empty();
                let f = if any_bold {
                    font_bold
                } else if any_italic {
                    font_italic
                } else {
                    font_regular
                };
                (11.0f32, 16.0f32, 7.0f32, f, PdfColor::new(32, 34, 38, 255))
            }
        };

        let raw_text: String = block.runs.iter().map(|r| r.text.as_str()).collect();
        let prefix = match block.kind.as_str() {
            "bullet" => "•  ".to_string(),
            "numbered" => {
                let s = format!("{num_counter}. ");
                num_counter += 1;
                s
            }
            _ => {
                num_counter = 1;
                String::new()
            }
        };
        let indent = match block.kind.as_str() {
            "bullet" | "numbered" | "quote" => 18.0f32,
            _ => 0.0f32,
        };

        let printable = if prefix.is_empty() {
            raw_text
        } else {
            format!("{prefix}{raw_text}")
        };
        let usable_w = (page_w - margin_x * 2.0 - indent).max(120.0);
        let approx_chars = ((usable_w / (font_size * 0.50)).floor() as usize).clamp(24, 120);
        let wrapped = wrap_words(&printable, approx_chars);

        for line in wrapped {
            if cursor_y - line_h < margin_bottom {
                page.regenerate_content()
                    .map_err(|e| format!("Regenerate content error: {e:?}"))?;
                page = pdf
                    .pages_mut()
                    .create_page_at_end(PdfPagePaperSize::from_inches(8.5, 11.0))
                    .map_err(|e| format!("Failed adding PDF page: {e:?}"))?;
                cursor_y = page_h - margin_top;
            }
            if !line.trim().is_empty() {
                let est_line_w = line.len() as f32 * font_size * 0.48;
                let x = match block.align.as_str() {
                    "center" => ((page_w - est_line_w) * 0.5).max(margin_x),
                    "right" => (page_w - margin_x - est_line_w).max(margin_x),
                    _ => margin_x + indent,
                };
                let mut obj = page
                    .objects_mut()
                    .create_text_object(
                        PdfPoints::new(x),
                        PdfPoints::new(cursor_y - font_size),
                        &line,
                        font,
                        PdfPoints::new(font_size),
                    )
                    .map_err(|e| format!("Failed writing text object: {e:?}"))?;
                let _ = obj.set_fill_color(color);
            }
            cursor_y -= line_h;
        }
        cursor_y -= space_after;
    }

    page.regenerate_content()
        .map_err(|e| format!("Regenerate content error: {e:?}"))?;

    let pdf_bytes = pdf
        .save_to_bytes()
        .map_err(|e| format!("Failed serializing converted PDF: {e:?}"))?;

    if open_in_engine {
        let title = docx.title.trim_end_matches(".docx").to_string() + ".pdf";
        let doc_id = NEXT_DOC_ID.fetch_add(1, Ordering::Relaxed);
        let info = collect_document_info(doc_id, title.clone(), &pdf);
        docs.insert(doc_id, (title, pdf));
        Ok((Some(info), pdf_bytes))
    } else {
        Ok((None, pdf_bytes))
    }
}

pub fn create_pdf_from_docx_doc(doc: DocxDocument, open_in_engine: bool) -> Result<(Option<DocumentOpenInfo>, Vec<u8>), String> {
    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::CreatePdfFromDocx {
        doc,
        open_in_engine,
        reply,
    })
    .map_err(|_| "PDFium worker stopped".to_string())?;
    rx.recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())?
}

pub fn export_pdf_to_docx(
    doc_id: u64,
    page_count: usize,
    title: &str,
    dest_path: &Path,
) -> Result<(), String> {
    let mut blocks = Vec::new();
    let clean_title = title.trim_end_matches(".pdf").to_string();
    blocks.push(DocxBlock {
        id: "h_title".to_string(),
        kind: "h1".to_string(),
        align: "left".to_string(),
        runs: vec![DocxRun {
            text: clean_title.clone(),
            bold: true,
            italic: false,
            underline: false,
        }],
        rows: Vec::new(),
    });

    let mut seq = 1usize;
    for page_idx in 0..page_count {
        if page_idx > 0 {
            blocks.push(DocxBlock {
                id: format!("p_hdr_{seq}"),
                kind: "h3".to_string(),
                align: "left".to_string(),
                runs: vec![DocxRun {
                    text: format!("Page {}", page_idx + 1),
                    bold: true,
                    italic: false,
                    underline: false,
                }],
                rows: Vec::new(),
            });
            seq += 1;
        }
        let text = get_page_text(doc_id, page_idx)?;
        for para in text.split("\r\n\r\n").flat_map(|s| s.split("\n\n")) {
            let normalized = para
                .lines()
                .map(str::trim)
                .filter(|l| !l.is_empty())
                .collect::<Vec<_>>()
                .join(" ");
            if normalized.is_empty() {
                continue;
            }
            blocks.push(DocxBlock {
                id: format!("p_{seq}"),
                kind: "paragraph".to_string(),
                align: "left".to_string(),
                runs: vec![DocxRun {
                    text: normalized,
                    bold: false,
                    italic: false,
                    underline: false,
                }],
                rows: Vec::new(),
            });
            seq += 1;
        }
    }

    let docx = DocxDocument {
        title: format!("{clean_title}.docx"),
        blocks,
    };
    docx_engine::save_docx_file(&docx, dest_path.to_str().unwrap_or("export.docx"))
}

pub fn sign_document_pkcs12_to_file(
    doc_id: u64,
    identity_id: &str,
    p12_password: &str,
    reason: &str,
    dest_path: &Path,
) -> Result<(), String> {
    use base64::Engine;
    let b64 = security::load_signing_identity(identity_id)?;
    let p12_bytes = base64::engine::general_purpose::STANDARD
        .decode(b64.trim())
        .map_err(|e| format!("Failed decoding saved .p12 certificate: {e}"))?;

    let tx = worker()?;
    let (reply, rx) = mpsc::sync_channel(1);
    tx.send(PdfCommand::SaveBytes { doc_id, reply })
        .map_err(|_| "PDFium worker stopped".to_string())?;
    let pdf_bytes = rx
        .recv()
        .map_err(|_| "PDFium worker dropped reply".to_string())??;

    let signed = signing::sign_pdf_bytes_pkcs12(&pdf_bytes, &p12_bytes, p12_password, reason)?;
    let dest_str = dest_path
        .to_str()
        .ok_or_else(|| "Invalid UTF-8 destination path".to_string())?;
    security::write_atomic(dest_str, &signed)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_pdf_bytes(pages: usize) -> Vec<u8> {
        let mut pdf = b"%PDF-1.4\n".to_vec();
        let mut offsets = Vec::new();
        let mut kids = String::new();
        // 1: Catalog, 2: Pages, 3: Font, then for each page: Page obj + Contents obj
        let o1 = pdf.len();
        pdf.extend_from_slice(b"1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        offsets.push(o1);

        for i in 0..pages {
            let page_obj_id = 4 + i * 2;
            kids.push_str(&format!("{page_obj_id} 0 R "));
        }
        let o2 = pdf.len();
        pdf.extend_from_slice(
            format!("2 0 obj << /Type /Pages /Kids [{kids}] /Count {pages} >> endobj\n")
                .as_bytes(),
        );
        offsets.push(o2);

        let o3 = pdf.len();
        pdf.extend_from_slice(
            b"3 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n",
        );
        offsets.push(o3);

        for i in 0..pages {
            let page_id = 4 + i * 2;
            let content_id = page_id + 1;
            let op = pdf.len();
            pdf.extend_from_slice(
                format!(
                    "{page_id} 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Resources << /Font << /F1 3 0 R >> >> /Contents {content_id} 0 R >> endobj\n"
                )
                .as_bytes(),
            );
            offsets.push(op);

            let oc = pdf.len();
            let stream = format!("BT /F1 14 Tf 20 100 Td (Hello Liquid Glass Page {}) Tj ET", i + 1);
            pdf.extend_from_slice(
                format!(
                    "{content_id} 0 obj << /Length {} >> stream\n{stream}\nendstream endobj\n",
                    stream.len()
                )
                .as_bytes(),
            );
            offsets.push(oc);
        }

        let total = offsets.len() + 1;
        let xref = pdf.len();
        pdf.extend_from_slice(format!("xref\n0 {total}\n0000000000 65535 f \n").as_bytes());
        for off in offsets {
            pdf.extend_from_slice(format!("{off:010} 00000 n \n").as_bytes());
        }
        pdf.extend_from_slice(
            format!("trailer << /Size {total} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n")
                .as_bytes(),
        );
        pdf
    }

    #[test]
    fn pdfium_opens_renders_extracts_and_searches() {
        let info = open_document_bytes(sample_pdf_bytes(1), "unit-test.pdf", None)
            .expect("pdfium should load sample pdf");
        assert_eq!(info.page_count, 1);

        let mut buf = vec![0u8; 64 * 64 * 4];
        render_page_into_bgra(info.doc_id, 0, 64, 64, &mut buf).expect("render page");
        assert!(buf.iter().any(|&b| b != 0));

        let text = get_page_text(info.doc_id, 0).expect("extract text");
        assert!(text.contains("Liquid Glass"), "got text: {text}");

        let spans = get_page_text_spans(info.doc_id, 0).expect("text spans");
        assert!(!spans.is_empty());

        let hits = search_document(info.doc_id, "Liquid", 10).expect("search");
        assert!(!hits.is_empty());

        assert!(close_document(info.doc_id));
    }

    #[test]
    fn pdfium_phase3_reorder_split_image_and_annotations() {
        let info = open_document_bytes(sample_pdf_bytes(3), "multi.pdf", None)
            .expect("open 3-page pdf");
        assert_eq!(info.page_count, 3);

        // Stamp a tiny 4x4 PNG onto page 0.
        let img = image::RgbaImage::from_pixel(4, 4, image::Rgba([10, 132, 255, 255]));
        let mut png_bytes = Vec::new();
        image::DynamicImage::ImageRgba8(img)
            .write_to(&mut Cursor::new(&mut png_bytes), image::ImageFormat::Png)
            .unwrap();
        add_image_bytes(info.doc_id, 0, png_bytes, 0.2, 0.3, 0.25).expect("stamp image");

        // Add highlight and inspect annotations.
        add_highlight(info.doc_id, 0, 0.1, 0.4, 0.7, 0.5, 0x88FFEB3B).expect("highlight");
        let annots = get_annotations(info.doc_id).expect("list annots");
        assert!(!annots.is_empty());
        delete_annotation(info.doc_id, 0, annots[0].annot_index).expect("delete annot");

        // Reorder pages [2, 0, 1].
        let reordered = reorder_pages(info.doc_id, vec![2, 0, 1]).expect("reorder");
        assert_eq!(reordered.page_count, 3);
        let p0_text = get_page_text(info.doc_id, 0).unwrap();
        assert!(p0_text.contains("Page 3"), "expected Page 3 first, got: {p0_text}");

        // Split into 2-page chunks in tempdir.
        let dir = tempfile::tempdir().unwrap();
        let count = split_document(info.doc_id, 3, 2, dir.path().to_str().unwrap(), "chunk")
            .expect("split");
        assert_eq!(count, 2);

        let meta = get_metadata(info.doc_id).expect("metadata");
        assert_eq!(meta.page_count, 3);

        assert!(close_document(info.doc_id));
    }

    #[test]
    fn pdfium_phase4_undo_redo_combine_and_forms() {
        let info = open_document_bytes(sample_pdf_bytes(2), "undo.pdf", None).expect("open 2p");
        assert_eq!(info.page_count, 2);

        let after_del = delete_page(info.doc_id, 1).expect("delete page 2");
        assert_eq!(after_del.page_count, 1);

        let undone = undo_document(info.doc_id).expect("undo delete");
        assert_eq!(undone.page_count, 2);

        let redone = redo_document(info.doc_id).expect("redo delete");
        assert_eq!(redone.page_count, 1);

        let fields = get_form_fields(info.doc_id, 0).expect("form fields query");
        assert!(fields.is_empty());

        let dir = tempfile::tempdir().unwrap();
        let f1 = dir.path().join("a.pdf");
        let f2 = dir.path().join("b.pdf");
        std::fs::write(&f1, sample_pdf_bytes(1)).unwrap();
        std::fs::write(&f2, sample_pdf_bytes(2)).unwrap();

        let combined = combine_pdf_files(
            vec![f1.to_string_lossy().into(), f2.to_string_lossy().into()],
            "Combined.pdf",
        )
        .expect("combine files");
        assert_eq!(combined.page_count, 3);

        assert!(close_document(info.doc_id));
        assert!(close_document(combined.doc_id));
    }

    #[test]
    fn pdfium_phase5_docx_to_pdf_export_docx_and_undo_flags() {
        let docx = DocxDocument {
            title: "Brief.docx".to_string(),
            blocks: vec![
                DocxBlock {
                    id: "b1".to_string(),
                    kind: "h1".to_string(),
                    align: "left".to_string(),
                    runs: vec![DocxRun {
                        text: "Minimal Studio Spec".to_string(),
                        bold: true,
                        italic: false,
                        underline: false,
                    }],
                    rows: Vec::new(),
                },
                DocxBlock {
                    id: "b2".to_string(),
                    kind: "paragraph".to_string(),
                    align: "left".to_string(),
                    runs: vec![DocxRun {
                        text: "Generated inside Vnyl Rust core.".to_string(),
                        bold: false,
                        italic: false,
                        underline: false,
                    }],
                    rows: Vec::new(),
                },
            ],
        };

        let (info_opt, pdf_bytes) = create_pdf_from_docx_doc(docx, true).expect("docx->pdf");
        let info = info_opt.expect("opened doc info");
        assert!(pdf_bytes.starts_with(b"%PDF-"));
        assert_eq!(info.page_count, 1);

        let extracted = get_page_text(info.doc_id, 0).expect("extract converted pdf text");
        assert!(extracted.contains("Minimal Studio Spec"), "got: {extracted}");

        let dir = tempfile::tempdir().unwrap();
        let out_docx = dir.path().join("exported.docx");
        export_pdf_to_docx(info.doc_id, info.page_count, "Brief.pdf", &out_docx)
            .expect("export pdf to docx");
        let parsed_back = docx_engine::parse_docx_file(out_docx.to_str().unwrap())
            .expect("parse exported docx");
        let combined_text: String = parsed_back
            .blocks
            .iter()
            .flat_map(|b| b.runs.iter().map(|r| r.text.as_str()))
            .collect::<Vec<_>>()
            .join(" ");
        assert!(combined_text.contains("Minimal Studio Spec"));

        let (can_u, can_r) = get_undo_state(info.doc_id).expect("undo state initial");
        assert!(!can_u && !can_r);
        add_highlight(info.doc_id, 0, 0.1, 0.1, 0.5, 0.2, 0x88FFEB3B).expect("highlight");
        let (can_u2, can_r2) = get_undo_state(info.doc_id).expect("undo state after edit");
        assert!(can_u2 && !can_r2);

        assert!(close_document(info.doc_id));
    }
}
