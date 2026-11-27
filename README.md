# Vnyl 0.9.0

A fast, minimal Windows PDF & Word (`.docx`) desktop studio built on a **Rust native core** (`pdfium-render` + OOXML + CMS/PKCS#7) and **Kotlin / Compose Multiplatform Desktop (Skiko)** single-canvas Liquid Glass UI.

## Highlights in 0.9.0

- **Rust + Kotlin/Skiko Architecture**: Migrated from Tauri/React/PDF.js to a multi-threaded Rust (`vnyl-core`) PDFium & OOXML engine paired with hardware-accelerated Skiko `RuntimeEffect` Liquid Glass refraction and morphing pills.
- **Minimal Monochrome Liquid Glass UI**: Custom `MINIMAL_CANVAS_SKSL` matte obsidian & alabaster studio backdrop with premultiplied SDF anti-aliasing and crisp sapphire focal accents.
- **PDF Viewing & Editing**:
  - High-speed BGRA `DirectByteBuffer` page rendering, GPU Night & Sepia reading filters, search, outline & thumbnail drawer, interactive vertical page scrubber, 2D zoom/pan, and Two-Page Book Spread view.
  - Highlights, freehand ink, text notes, drawn/typed signatures, checkmark/cross stamps, image insertion, inline annotation deletion, and interactive AcroForm fields.
  - Page Organizer (reorder, rotate CW/CCW, delete, extract), split by chunks, merge/combine multi-PDFs, 12-step PDF Undo/Redo, and encrypted PDF password prompt.
- **Word (`.docx` & `.doc`) Support**:
  - Open, edit, and save `.docx` documents directly in-app (headings, paragraphs, bullet/numbered lists, quotes, alignment, bold/italic/underline, editable tables, 24-step undo/redo).
  - Built-in zero-dependency `.docx` → PDF conversion and PDF → `.docx` export, plus optional Microsoft Word / LibreOffice layout conversion for legacy `.doc` and complex layouts.
- **Digital Signing & Safety**:
  - PKCS#7 / CMS `/adbe.pkcs7.detached` cryptographic PDF signing using `.p12` keystores stored in Windows Credential Manager via Rust `keyring`.
  - Unsaved changes confirmation guard before closing modified tabs or exiting.

## Running & Building (Windows x64)
### Quick Launch
```powershell
.\vnyl.bat
# or
.\run.ps1
```

### Build & Test from Source
Requires JDK 21 and the Rust toolchain (`cargo`):

```powershell
cargo test -p vnyl-core --offline
.\gradlew.bat test createDistributable packageExe packageMsi --no-daemon --console=plain
```

Packaged installers and standalone executables are written to `build/compose/binaries/main/`.
