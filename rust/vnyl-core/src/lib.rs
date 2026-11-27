//! JNI bridge exposing Vnyl's Rust PDFium + security core to the Kotlin/Skiko
//! Compose Multiplatform Desktop UI.

pub mod docx_engine;
pub mod pdf_engine;
pub mod security;
pub mod signing;

use jni::objects::{JByteArray, JByteBuffer, JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

fn throw_runtime(env: &mut JNIEnv, msg: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", msg);
}

fn jstr_to_string(env: &mut JNIEnv, s: JString) -> Result<String, String> {
    env.get_string(&s)
        .map(|js| js.into())
        .map_err(|e| format!("Invalid JNI string: {e}"))
}

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(js) => js.into_raw(),
        Err(e) => {
            throw_runtime(env, &format!("Failed to allocate JNI string: {e}"));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_initPdfium(
    mut env: JNIEnv,
    _class: JClass,
    hint_dir: JString,
) -> jboolean {
    let hint = if hint_dir.is_null() {
        None
    } else {
        match jstr_to_string(&mut env, hint_dir) {
            Ok(s) if !s.is_empty() => Some(s),
            Ok(_) => None,
            Err(e) => {
                throw_runtime(&mut env, &e);
                return JNI_FALSE;
            }
        }
    };

    match pdf_engine::init_pdfium(hint.as_deref()) {
        Ok(_) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_openDocumentJson(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    password: JString,
) -> jstring {
    let path_str = match jstr_to_string(&mut env, path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    let pwd_str = if password.is_null() {
        None
    } else {
        match jstr_to_string(&mut env, password) {
            Ok(s) if !s.is_empty() => Some(s),
            _ => None,
        }
    };

    match pdf_engine::open_document_file(&path_str, pwd_str.as_deref()) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("JSON serialize failed: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_openDocumentBytesJson(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
    name: JString,
) -> jstring {
    let raw = match env.convert_byte_array(&bytes) {
        Ok(v) => v,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid byte array: {e}"));
            return std::ptr::null_mut();
        }
    };
    let name_str = match jstr_to_string(&mut env, name) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };

    match pdf_engine::open_document_bytes(raw, &name_str, None) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("JSON serialize failed: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_closeDocument(
    _env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jboolean {
    if pdf_engine::close_document(doc_id as u64) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_renderPageIntoBuffer(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    width_px: jint,
    height_px: jint,
    out_buffer: JByteBuffer,
) -> jboolean {
    let ptr = match env.get_direct_buffer_address(&out_buffer) {
        Ok(p) => p,
        Err(e) => {
            throw_runtime(&mut env, &format!("Expected direct ByteBuffer: {e}"));
            return JNI_FALSE;
        }
    };
    let cap = match env.get_direct_buffer_capacity(&out_buffer) {
        Ok(c) => c,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid direct ByteBuffer capacity: {e}"));
            return JNI_FALSE;
        }
    };

    // SAFETY: `ptr` and `cap` come directly from JNI's `GetDirectBufferAddress` /
    // `GetDirectBufferCapacity` for the live `JByteBuffer` passed for this call.
    let slice = unsafe { std::slice::from_raw_parts_mut(ptr, cap) };
    match pdf_engine::render_page_into_bgra(
        doc_id as u64,
        page_index.max(0) as usize,
        width_px,
        height_px,
        slice,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getOutlineJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jstring {
    match pdf_engine::get_outline(doc_id as u64) {
        Ok(items) => match serde_json::to_string(&items) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Outline JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getMetadataJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jstring {
    match pdf_engine::get_metadata(doc_id as u64) {
        Ok(meta) => match serde_json::to_string(&meta) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Metadata JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getPageText(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
) -> jstring {
    match pdf_engine::get_page_text(doc_id as u64, page_index.max(0) as usize) {
        Ok(text) => to_jstring(&mut env, &text),
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getPageTextSpansJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
) -> jstring {
    match pdf_engine::get_page_text_spans(doc_id as u64, page_index.max(0) as usize) {
        Ok(spans) => match serde_json::to_string(&spans) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Text spans JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getPageLinksJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
) -> jstring {
    match pdf_engine::get_page_links(doc_id as u64, page_index.max(0) as usize) {
        Ok(links) => match serde_json::to_string(&links) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Links JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getAnnotationsJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jstring {
    match pdf_engine::get_annotations(doc_id as u64) {
        Ok(items) => match serde_json::to_string(&items) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Annotations JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_deleteAnnotation(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    annot_index: jint,
) -> jboolean {
    match pdf_engine::delete_annotation(
        doc_id as u64,
        page_index.max(0) as usize,
        annot_index.max(0) as usize,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_searchDocumentJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    query: JString,
    max_hits: jint,
) -> jstring {
    let q = match jstr_to_string(&mut env, query) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    match pdf_engine::search_document(doc_id as u64, &q, max_hits.max(1) as usize) {
        Ok(hits) => match serde_json::to_string(&hits) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Search JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_addHighlight(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    left: jfloat,
    top: jfloat,
    right: jfloat,
    bottom: jfloat,
    argb: jint,
) -> jboolean {
    match pdf_engine::add_highlight(
        doc_id as u64,
        page_index.max(0) as usize,
        left,
        top,
        right,
        bottom,
        argb as u32,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_addInkStroke(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    xy_coords: JFloatArray,
    argb: jint,
    stroke_width_pts: jfloat,
) -> jboolean {
    let len = match env.get_array_length(&xy_coords) {
        Ok(l) => l as usize,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid float array: {e}"));
            return JNI_FALSE;
        }
    };
    let mut flat = vec![0f32; len];
    if let Err(e) = env.get_float_array_region(&xy_coords, 0, &mut flat) {
        throw_runtime(&mut env, &format!("Failed reading ink coords: {e}"));
        return JNI_FALSE;
    }
    let points: Vec<(f32, f32)> = flat.chunks_exact(2).map(|c| (c[0], c[1])).collect();
    match pdf_engine::add_ink_stroke(
        doc_id as u64,
        page_index.max(0) as usize,
        points,
        argb as u32,
        stroke_width_pts,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_addTextNote(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    norm_x: jfloat,
    norm_y: jfloat,
    text: JString,
    font_size_pts: jfloat,
    argb: jint,
) -> jboolean {
    let text_str = match jstr_to_string(&mut env, text) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::add_text_note(
        doc_id as u64,
        page_index.max(0) as usize,
        norm_x,
        norm_y,
        text_str,
        font_size_pts,
        argb as u32,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_addImageBytes(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    image_bytes: JByteArray,
    norm_x: jfloat,
    norm_y: jfloat,
    norm_width: jfloat,
) -> jboolean {
    let raw = match env.convert_byte_array(&image_bytes) {
        Ok(v) => v,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid image bytes: {e}"));
            return JNI_FALSE;
        }
    };
    match pdf_engine::add_image_bytes(
        doc_id as u64,
        page_index.max(0) as usize,
        raw,
        norm_x,
        norm_y,
        norm_width,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_rotatePageJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    delta_quarters: jint,
) -> jstring {
    match pdf_engine::rotate_page(doc_id as u64, page_index.max(0) as usize, delta_quarters) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Rotate JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_deletePageJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
) -> jstring {
    match pdf_engine::delete_page(doc_id as u64, page_index.max(0) as usize) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Delete JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_reorderPagesJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    order_arr: JIntArray,
) -> jstring {
    let len = match env.get_array_length(&order_arr) {
        Ok(l) => l as usize,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid order array: {e}"));
            return std::ptr::null_mut();
        }
    };
    let mut raw = vec![0i32; len];
    if let Err(e) = env.get_int_array_region(&order_arr, 0, &mut raw) {
        throw_runtime(&mut env, &format!("Failed reading order array: {e}"));
        return std::ptr::null_mut();
    }
    let order: Vec<usize> = raw.into_iter().map(|i| i.max(0) as usize).collect();
    match pdf_engine::reorder_pages(doc_id as u64, order) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Reorder JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_extractPagesToFile(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    pages_one_based: JString,
    dest_path: JString,
) -> jboolean {
    let pages = match jstr_to_string(&mut env, pages_one_based) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let dest = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::extract_pages_to_file(doc_id as u64, &pages, &dest) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_splitDocument(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_count: jint,
    chunk_size: jint,
    out_dir: JString,
    base_name: JString,
) -> jint {
    let dir_str = match jstr_to_string(&mut env, out_dir) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };
    let base_str = match jstr_to_string(&mut env, base_name) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };
    match pdf_engine::split_document(
        doc_id as u64,
        page_count.max(0) as usize,
        chunk_size.max(1) as usize,
        &dir_str,
        &base_str,
    ) {
        Ok(count) => count as jint,
        Err(e) => {
            throw_runtime(&mut env, &e);
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_mergePdfFileJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    source_path: JString,
) -> jstring {
    let path_str = match jstr_to_string(&mut env, source_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    match pdf_engine::merge_pdf_file(doc_id as u64, &path_str) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Merge JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_saveDocument(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    dest_path: JString,
) -> jboolean {
    let path_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::save_document(doc_id as u64, &path_str) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_exportPageImage(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    dest_path: JString,
    dpi: jint,
) -> jboolean {
    let path_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::export_page_image(
        doc_id as u64,
        page_index.max(0) as usize,
        &path_str,
        dpi.max(72) as u32,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_exportDocumentText(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_count: jint,
    title: JString,
    dest_path: JString,
    as_markdown: jboolean,
) -> jboolean {
    let title_str = match jstr_to_string(&mut env, title) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let path_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::export_document_text(
        doc_id as u64,
        page_count.max(0) as usize,
        &title_str,
        &path_str,
        as_markdown != JNI_FALSE,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_undoDocumentJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jstring {
    match pdf_engine::undo_document(doc_id as u64) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Undo JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_redoDocumentJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jstring {
    match pdf_engine::redo_document(doc_id as u64) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Redo JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_combinePdfFilesJson(
    mut env: JNIEnv,
    _class: JClass,
    paths_joined: JString,
    title: JString,
) -> jstring {
    let joined = match jstr_to_string(&mut env, paths_joined) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    let title_str = match jstr_to_string(&mut env, title) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    let paths: Vec<String> = joined
        .lines()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    match pdf_engine::combine_pdf_files(paths, &title_str) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Combine JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getFormFieldsJson(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
) -> jstring {
    match pdf_engine::get_form_fields(doc_id as u64, page_index.max(0) as usize) {
        Ok(fields) => match serde_json::to_string(&fields) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("Form fields JSON error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_setFormField(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_index: jint,
    annot_index: jint,
    value: JString,
    checked: jboolean,
) -> jboolean {
    let val_str = match jstr_to_string(&mut env, value) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::set_form_field(
        doc_id as u64,
        page_index.max(0) as usize,
        annot_index.max(0) as usize,
        &val_str,
        checked != JNI_FALSE,
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_saveSigningIdentity(
    mut env: JNIEnv,
    _class: JClass,
    id: JString,
    p12_base64: JString,
) -> jboolean {
    let id_str = match jstr_to_string(&mut env, id) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let b64_str = match jstr_to_string(&mut env, p12_base64) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match security::save_signing_identity(&id_str, &b64_str) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_loadSigningIdentity(
    mut env: JNIEnv,
    _class: JClass,
    id: JString,
) -> jstring {
    let id_str = match jstr_to_string(&mut env, id) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    match security::load_signing_identity(&id_str) {
        Ok(b64) => to_jstring(&mut env, &b64),
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_deleteSigningIdentity(
    mut env: JNIEnv,
    _class: JClass,
    id: JString,
) -> jboolean {
    let id_str = match jstr_to_string(&mut env, id) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match security::delete_signing_identity(&id_str) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_getUndoStateMask(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
) -> jint {
    match pdf_engine::get_undo_state(doc_id as u64) {
        Ok((can_undo, can_redo)) => {
            (if can_undo { 1 } else { 0 }) | (if can_redo { 2 } else { 0 })
        }
        Err(e) => {
            throw_runtime(&mut env, &e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_pickFolderNative(
    mut env: JNIEnv,
    _class: JClass,
    title: JString,
) -> jstring {
    let title_str = jstr_to_string(&mut env, title).unwrap_or_else(|_| "Select Folder".to_string());
    let picked = rfd::FileDialog::new()
        .set_title(&title_str)
        .pick_folder();
    match picked {
        Some(path) => to_jstring(&mut env, &path.to_string_lossy()),
        None => to_jstring(&mut env, ""),
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_parseDocxJson(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    let path_str = match jstr_to_string(&mut env, path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    match docx_engine::parse_docx_file(&path_str) {
        Ok(doc) => match serde_json::to_string(&doc) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("DOCX JSON serialize failed: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_saveDocxFromJson(
    mut env: JNIEnv,
    _class: JClass,
    docx_json: JString,
    dest_path: JString,
) -> jboolean {
    let json_str = match jstr_to_string(&mut env, docx_json) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let dest_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let doc: docx_engine::DocxDocument = match serde_json::from_str(&json_str) {
        Ok(d) => d,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid DOCX JSON payload: {e}"));
            return JNI_FALSE;
        }
    };
    match docx_engine::save_docx_file(&doc, &dest_str) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_convertDocxJsonToPdfJson(
    mut env: JNIEnv,
    _class: JClass,
    docx_json: JString,
) -> jstring {
    let json_str = match jstr_to_string(&mut env, docx_json) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    let doc: docx_engine::DocxDocument = match serde_json::from_str(&json_str) {
        Ok(d) => d,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid DOCX JSON payload: {e}"));
            return std::ptr::null_mut();
        }
    };
    match pdf_engine::create_pdf_from_docx_doc(doc, true) {
        Ok((Some(info), _)) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("PDF info serialize error: {e}"));
                std::ptr::null_mut()
            }
        },
        Ok((None, _)) => {
            throw_runtime(&mut env, "Missing opened PDF info");
            std::ptr::null_mut()
        }
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_exportDocxJsonToPdfFile(
    mut env: JNIEnv,
    _class: JClass,
    docx_json: JString,
    dest_path: JString,
) -> jboolean {
    let json_str = match jstr_to_string(&mut env, docx_json) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let dest_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let doc: docx_engine::DocxDocument = match serde_json::from_str(&json_str) {
        Ok(d) => d,
        Err(e) => {
            throw_runtime(&mut env, &format!("Invalid DOCX JSON payload: {e}"));
            return JNI_FALSE;
        }
    };
    match pdf_engine::create_pdf_from_docx_doc(doc, false) {
        Ok((_, bytes)) => match security::write_atomic(&dest_str, &bytes) {
            Ok(()) => JNI_TRUE,
            Err(e) => {
                throw_runtime(&mut env, &e);
                JNI_FALSE
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_detectOfficeConverterJson(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let info = docx_engine::detect_office_converter();
    match serde_json::to_string(&info) {
        Ok(json) => to_jstring(&mut env, &json),
        Err(e) => {
            throw_runtime(&mut env, &format!("Office info serialize failed: {e}"));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_convertWordViaOfficeToPdfJson(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    let path_str = match jstr_to_string(&mut env, path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    let title = std::path::Path::new(&path_str)
        .file_stem()
        .and_then(|s| s.to_str())
        .map(|s| format!("{s}.pdf"))
        .unwrap_or_else(|| "Converted.pdf".to_string());
    let pdf_bytes = match docx_engine::convert_word_file_via_office(&path_str) {
        Ok(b) => b,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return std::ptr::null_mut();
        }
    };
    match pdf_engine::open_document_bytes(pdf_bytes, &title, None) {
        Ok(info) => match serde_json::to_string(&info) {
            Ok(json) => to_jstring(&mut env, &json),
            Err(e) => {
                throw_runtime(&mut env, &format!("PDF info serialize error: {e}"));
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            throw_runtime(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_exportPdfToDocx(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    page_count: jint,
    title: JString,
    dest_path: JString,
) -> jboolean {
    let title_str = jstr_to_string(&mut env, title).unwrap_or_else(|_| "Document.pdf".to_string());
    let dest_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::export_pdf_to_docx(
        doc_id as u64,
        page_count.max(0) as usize,
        &title_str,
        std::path::Path::new(&dest_str),
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_app_vnyl_native_VnylNative_signPdfPkcs12ToFile(
    mut env: JNIEnv,
    _class: JClass,
    doc_id: jlong,
    identity_id: JString,
    password: JString,
    reason: JString,
    dest_path: JString,
) -> jboolean {
    let id_str = match jstr_to_string(&mut env, identity_id) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    let pwd_str = jstr_to_string(&mut env, password).unwrap_or_default();
    let reason_str = jstr_to_string(&mut env, reason).unwrap_or_default();
    let dest_str = match jstr_to_string(&mut env, dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JNI_FALSE;
        }
    };
    match pdf_engine::sign_document_pkcs12_to_file(
        doc_id as u64,
        &id_str,
        &pwd_str,
        &reason_str,
        std::path::Path::new(&dest_str),
    ) {
        Ok(()) => JNI_TRUE,
        Err(e) => {
            throw_runtime(&mut env, &e);
            JNI_FALSE
        }
    }
}
