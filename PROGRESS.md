# PROGRESS.md - Vnyl (formerly Folio): STATE AT v0.8.0 (finished, shippable)

Resume instruction for a new chat: "Continue the Vnyl project from PROGRESS.md" (attach this file + vnyl-0.8.0-source.zip).
The user asked (session 14) to STOP expanding the plan and finish a polished, usable product. That is done; everything below the "Backlog" heading is optional future work.

## What v0.8.0 contains (all verified; see CHANGELOG.md and docs/user-guide.md)
Desktop app (Tauri 2 + Rust) with the Apple-style Liquid Glass UI (Frosted/Clear/Classic looks, light/dark). Tabs for many PDFs, split view with linked scroll, compare documents (visual + text), markup (highlight/underline/strikeout/squiggly, click to edit, persistent toolbar), recent files, keyboard-shortcuts dialog, and a Tools gallery with 16 working tools: PDF to images, PDF to text, Images to PDF, OCR, Merge, Organize pages, Split, Extract, Compress, Add text, Markup toolbar, Add/Digitally sign, Compare, Split view, Print. Native Save dialog + `write_export` Rust command on desktop. Vnyl app icon. README/user guide/desktop docs rewritten. Version bumped to 0.8.0 (version-parity scripts pass).
Verification done: 889 unit tests, eslint, tsc, vite build, 7 Rust tests (`cargo test --lib`), axe accessibility audit = 0 violations on start/document/hub/tool panel/shortcuts, real-browser runs (headless Chromium) of tabs, split, compare, converters (outputs checked with PyMuPDF/unzip/PIL), responsive checks at 1280, 900x600 and 420 wide.
NOT verified (cannot be done in the sandbox): the actual desktop window and native dialogs, Windows/macOS installers. Run `npm ci && npm run tauri dev` (docs/desktop.md); CI (ci.yml) already builds all three OSes.

## Backlog (not started; do only if asked)
Crop, Redact, Password protect, Flatten, Draw tools (pen/shapes/stamps), LibreOffice bridge for Word/Excel/PowerPoint/PDF-A (Rust command that runs `soffice --headless`), HEIC, batch queue + presets, dropping image files onto the desktop window (tauri drag-drop is intercepted globally for PDFs), migrating internal `folio` identifiers (CSS prefix, storage keys, `folio://`, bundle id) to `vnyl`, PDF compare for scans (OCR), tab persistence across restarts.

---
# HISTORY (earlier plan and per-part notes, kept for reference)

# PROGRESS.md - Folio: Xodo-style features + Liquid Glass theme

Resume instruction for a new chat: "Continue the Folio project from PROGRESS.md".
Base repo: https://github.com/owenpkent/folio (Tauri 2 + React 19 + TS + PDF.js + Zustand).
Rules: keep layout sizes unchanged in theme work; every action goes through the command registry (src/commands/); respect a11y (reduced motion/transparency, forced-colors). Run `npx prettier --write`, `npm run typecheck`, `npm run lint`, `npx vite build` before handing off.

## Part 4c status (Images to PDF) - session 13: DONE, verified in headless Chromium + PyMuPDF
Done (src/features/convert/): imagesToPdf.ts (layoutPage: Fit-to-image at 96dpi capped 1440pt, A4/Letter, auto/portrait/landscape, margins ignored if no room; jpegOrientation = EXIF 1-8 reader, both byte orders; sniffImage by magic bytes; imagesToPdf with pdf-lib: raw JPEG/PNG embedded losslessly, sideways JPEG re-drawn upright via createImageBitmap('from-image') -> JPEG q.92, WebP/BMP/GIF(first frame) -> canvas -> PNG; per-file error names the file), ImagesToPdfPanel.tsx (hidden <input multiple> + in-panel drop zone, thumbnail list with up/down/remove, sort by name, Segmented options, file name, progress/cancel, result: 'Open it' via loadSource -> new tab, 'Save PDF...' via saveBytes = native dialog on desktop), useJob.ts + useReveal.ts extracted from panels.tsx, catalog entry now ready, ToolPanel union extended. tsconfig lib += ES2022.Error (Error cause).
Verified: 6 mixed images (sideways EXIF JPEG, PNG with alpha, GIF, WebP, BMP, plain JPEG) -> 6 pages, A4 auto orientation correct, sideways photo upright (checked pixels), plain JPEG embedded unchanged, transparent corner white, producer 'Vnyl'; saved + opened as a tab. Tests: layout/EXIF/sniff (8). Suite 871 passed; eslint/typecheck/build pass. Script: screenshot-tools/img2pdf.mjs.
Desktop note: the desktop window intercepts OS file drops globally (tauri dragDropEnabled, handled in App.tsx for PDFs), so the in-panel drop zone only works in the browser build; the 'Choose images...' button (HTML file input) works on desktop. Possible later: handle image paths in the global onDragDropEvent when the hub is open (needs a read command with an image-extension allowlist, like read_document).
Limits: GIF/WebP/BMP are stored as PNG (bigger than JPEG for photos); animated GIF = first frame; no HEIC (WebView2 cannot decode it; macOS WebKit can - could be probed); very many large images are all processed in memory.
NEXT (4d): Split PDF (ranges / every N / each page -> zip or folder via saveAllToFolder), Extract pages (-> new tab), Delete pages already in Organize. Compress (pdf-lib re-save with object streams + optional rasterise-to-JPEG preset; report before/after size), Crop (per-page margins, preview). All as hub panels reusing useSourceEngine/useJob/saveOutput. Then 4e presets/batch, 4f LibreOffice bridge (Rust command: detect soffice, convert Word/Excel/PowerPoint <-> PDF).

## Part 4a/4b status (Tools hub + first converters) - session 11: DONE, verified in headless Chromium
Done (src/features/convert/): zip.ts (STORE-method ZIP writer + crc32, no dependency), pageRange.ts (`1-3, 5, 8-`), naming.ts (template {name}/{page}/{total}, sanitise, unique), engines.ts (pdfToImages: renders at exact DPI by dividing out renderPage's max(2,dpr) backing scale, flattens on white, toBlob; pdfToText txt/md; downloadBlob; MAX_IMAGE_SIDE 4096 = engine canvas cap, pages reduced are reported), useSourceEngine.ts (private PdfJsEngine per chosen tab), panels.tsx (PdfToImagesPanel, PdfToTextPanel, Results with zip, useJob with progress/cancel, scroll-into-view of results), catalog.ts (5 categories, 28 tools; status ready|soon|desktop; ready tools either open a `panel` or run an existing `command`), ToolsHub.tsx (modal wide, search, cards as aria-disabled buttons with reason in title), store.ts, commands.ts (tools.hub = Mod+Shift+K), Tools menu: 'All tools…' first. Glass + base CSS added.
Verified outside the app: downloaded zip passes `unzip -t` and python zipfile CRC; PNGs 1275x1650 at 150 DPI, JPG 2550x3300 at 300 DPI; text file content correct; bad range disables Convert with a message; Compare card blocked with one doc.
Tests: pageRange, naming, zip, catalog (21). Suite 855 passed; eslint/typecheck/build pass. Browser script: screenshot-tools/hub.mjs.
Limits: no HEIC/WebP-input; text extraction loses layout (single string per page); conversions run one at a time (no multi-file batch queue yet); output is held in memory (very large page counts at 300 DPI can be heavy); PDF-A/Word/Excel/PPT are desktop-only placeholders.
NEXT (4c): Images -> PDF. Plan: ImagesToPdfPanel in panels.tsx (add to ToolPanel union in catalog.ts + HubView, flip catalog `images-to-pdf` to ready with panel), file picker/drop zone for multiple images (accept jpg/png/webp/bmp/gif; reorder list; page size Fit/A4/Letter + margin + orientation), build with pdf-lib: embedJpg/embedPng directly, other formats via canvas -> PNG blob; progress; result opens as a NEW TAB (use loadSource with bytes, which the workspace turns into a tab) and offers Download. Then (4d) Split (ranges / every N / each page -> zip), Extract pages, Compress (rasterise-to-JPEG preset + 'remove metadata/optimise' via pdf-lib useObjectStreams), Crop. (4e) presets + batch queue + drag-drop of many files into the hub, recent results.

## Part 3d status (Compare documents) - session 10: DONE, verified in headless Chromium
Done (src/features/compare/): diff.ts (diffWords = word LCS with prefix/suffix trim + huge-region guard; diffStats; diffPixels red=only in A, green=only in B, threshold 48), pixels.ts (renderPixels, fitPixels for different page sizes), store.ts, CompareModal.tsx (two private PdfJsEngine instances, loads tabs via workspace getTabSource; background scan of every shared page = text diff + visual ratio at scale .5 -> flags changed pages; selected page re-rendered at 1.2; Visual/Text segmented control; swap; prev/next difference; handles different page counts), commands.ts (tools.compare, enabled with >=2 tabs; toast otherwise), Tools menu now always exists and starts with 'Compare documents…'.
Also: components/common/Segmented.tsx (radio-group segmented control, spring thumb under glass; reuse in Tools hub), workspace/tabSource.ts (shared getTabSource, used by split + compare), fixed `.folio-modal--wide` never applying (affected Organize pages too).
Tests: diff.ts (9), MenuBar tests updated (Tools always present; Compare disabled without 2 tabs). Suite 843 passed; eslint/typecheck/build pass. Browser scripts: screenshot-tools/compare.mjs, mkcmp.mjs (builds cmpA/cmpB with known differences), organize.mjs.
Verified: pages 1 and 3 flagged, page 2 not; text diff shows exactly the planted words; next-difference jumps; swap rescans.
Limits: visual diff is noisy when edits reflow text (use Text mode); scan renders every shared page at low scale so very long documents are slow to finish flagging (it is cancellable by closing); no OCR for scanned pages; compare uses the open TABS only (no file picker inside the dialog yet).
NEXT: Part 4 Tools hub + convert engines (see plan). Suggested order inside Part 4: (4a) hub modal UI with Xodo-style categories and cards (use Segmented for category tabs; cards for Convert from PDF / Convert to PDF / Organize / Edit / View, disabled desktop-only cards with tooltip), (4b) PDF -> images + text (first working converter, plus queue/progress + zip via fflate; check package.json), (4c) images -> PDF, (4d) split / extract / compress, (4e) presets + naming template.

## RENAME: the app is now called "Vnyl" (session 9). Folio -> Vnyl everywhere user-visible.
Done by search/replace of the word "Folio" (112 files: UI strings, docs, README, index.html, tauri productName/window title, Rust comments/strings, extensions). Use "Vnyl" in all new UI text, docs, tests.
Deliberately KEPT as "folio" (internal identifiers; renaming them would drop users' saved data or break integrations): CSS class prefix `folio-*` (hundreds of rules), localStorage keys `folio.*` / `folio:resume-after-update`, `folio://` deep-link scheme + `folio:open-pdf` event, the `Folio-Path` HTTP header (TS <-> Rust contract), Tauri bundle identifier `app.folio.viewer`, package name `folio`, repo URL owenpkent/folio, `FOLIO_*` env/globals, and the legacy .p12 test fixture whose certificate organization is literally "Folio Legacy". A later, separate task could migrate these (needs storage-key migration + Rust + updater endpoint changes).
Console log prefix is still `[folio]`.

## GLASS MATERIAL v2 (session 9): ideas taken from https://github.com/sdegenaar/liquid_glass_widgets (Flutter; cloned in the sandbox because the fetch tool is blocked for GitHub; can be re-cloned with git)
Not reusable as code (Flutter/Dart + fragment shaders); its DESIGN VALUES were translated to CSS in src/theme/glass.css:
- iOS 27 material numbers: saturation 2.1 (light) / 1.4 (dark), frost opacity ~.62-.66 with heavier tint for menus/modals (.82-.92), small blur (22px; 34px on menus over text), radii 16/8/20 + capsule buttons.
- Rim light (top inner highlight) + rim shade (bottom) + inner lens band via layered inset box-shadows (--glass-highlight); diagonal specular sheen gradient (--glass-sheen).
- Spring motion: CSS linear() easing for the package's "bouncy" and "snappy" presets (--glass-spring, --glass-spring-snappy); pop-in animation for menus/toasts; jelly press: 60ms in (scale .9 + ambient lift --glass-press), 500ms spring out; all disabled under prefers-reduced-motion.
- "Glass look" setting (Frosted | Clear), store key folio.glass, attr data-glass; Clear = lighter frost, more transparency.
Not done (possible later): real refraction/lens displacement (SVG feDisplacementMap works only in Chromium; the package itself approximates on web), progressive blur ramp for popovers, content-aware text colour (GlassContentAwareScope), scroll-edge fade under bars, morphing toolbar items, glass switch/slider/segmented controls (add when Part 4/settings need them: segmented control with sliding glass thumb is a good fit for the Tools hub tabs).

## PLATFORM DECISION (session 12, user request): **Vnyl is a DESKTOP APPLICATION** (Tauri 2 + Rust). The browser build is secondary.
Rules for all future work:
1. Every feature must work in the Tauri desktop app first. Anything that saves files MUST go through native dialogs + Rust, never only a browser download link (blob downloads via <a download> are unreliable in desktop webviews). Use `saveOutput` / `saveAllToFolder` (src/features/convert/saveOutput.ts); PDFs use `saveBytes` (features/export/saveDocument.ts).
2. The webview gets NO general fs-write capability. New write paths = a narrow Rust command with a closed extension allowlist, like `write_document` (.pdf) and `write_export` (png jpg jpeg webp zip txt md html; `EXPORT_EXTENSIONS` in src-tauri/src/lib.rs). Both take raw bytes as the invoke body and the path in the `Folio-Path` header (percent-encoded).
3. "Desktop-only" features are now just features: Office conversions (Word/Excel/PowerPoint <-> PDF, PDF/A) are planned through a Rust command that finds and runs LibreOffice (`soffice --headless --convert-to ...`) with a temp dir, detection UI, and a clear "install LibreOffice" message. Catalog status `desktop` now means "needs LibreOffice" (badge text updated).
4. Desktop extras worth doing: file associations/"Open with" (already wired via take_launch_file), recent files, native menus, window state persistence, multiple windows, auto-update (plugin already present), drag files onto the dock/taskbar icon, print via OS.
5. See docs/desktop.md (build/run, table of desktop vs browser, write-command rules). CI already builds Tauri on Windows/macOS/Linux (ci.yml); releasing: docs/releasing.md.
6. Sandbox notes: Rust compile-checks work here: `apt-get install cargo-1.91 rustc-1.91 libwebkit2gtk-4.1-dev libgtk-3-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev libsoup-3.0-dev` then `PATH=/usr/lib/rust-1.91/bin:$PATH cargo test --lib` in src-tauri (needs 1.90+ because of the `muda` crate; first build takes ~10 minutes). A real GUI window / installer cannot be produced or screenshotted in this sandbox (no display, Linux only); the front end is verified in headless Chromium, Rust by `cargo test`.
App icon: scripts/make-icon.py -> src-tauri/icons/source/vnyl-icon.png -> `npm run tauri icon ...` (done; 52 files regenerated).

## TARGET PRODUCT (user request, session 8): "make it like Xodo (https://xodo.com/), with MY UI changes + ALL features listed so far"
Meaning: Folio should end up feeling like Xodo's web app/PDF Studio, but with the Apple Liquid Glass UI (Part 1), plus Filee-style conversion, plus multi-PDF tabs and split view. Keep it a single coherent app; a Xodo-style "Tools hub" is the front door to everything below.

### Xodo catalogue (from xodo.com, 30+ tools) -> Folio status
Convert FROM PDF: Word, Excel, HTML, Text, PDF/A, PowerPoint, JPG, PNG  (HEIC->JPG/PNG)
  - PLAN: JPG, PNG, WEBP, Text, Markdown, HTML(basic) in-browser (Part 4). Word/Excel/PowerPoint/PDF-A: desktop-only via optional engine (LibreOffice/Ghostscript) -> show disabled with tooltip in browser.
Convert TO PDF: OCR Converter, Word, JPG, Excel, PNG, PowerPoint, HEIC, DICOM
  - PLAN: JPG/PNG/WEBP/BMP/GIF -> PDF in-browser (Part 4); OCR -> exists (src/features/ocr), expose in hub; HEIC via heic2any if license OK (check); Office formats desktop-only.
Organize: Merge, Split, Delete pages, Rotate, Rearrange
  - HAVE: merge (combine), delete/rotate/rearrange (pageops/organize modal). TODO: split UI (ranges / every N / by bookmarks) in Part 4/6.
Edit: PDF editor, Crop, DOCX editor, Edit PDF text, Compress, Sign, Redact, Fill forms, Flatten, Annotate
  - HAVE: text edit, sign, fill forms, annotate (Part 2). TODO: Crop, Compress, Redact, Flatten (Parts 6/7). DOCX editor: out of scope.
View: ASK PDF (AI chat), PDF Compare, PDF Viewer, Office Viewer, Text Compare, Validate PDF/A
  - HAVE: viewer, tabs, split view. TODO: PDF Compare (visual diff overlay + text diff; build on split view) = Part 3d; Ask PDF: check src/ai/ (Folio has an ai/ module + MCP) and surface in hub; Office viewer / PDF-A validation: desktop-only/out of scope for now.
Xodo blog hints worth copying: export PDF comments to Excel/CSV (annotations export), split by bookmarks, OCR + copy text from scan, markup construction drawings (measure tools later), secure/password-protect before sharing, fill forms w/o printing.
Xodo PDF Studio (desktop): offline OCR, automation (batch) -> our Convert queue + Tauri engines later.

## Plan (revised in session 6: Filee features + multi-PDF + split view added)
- [x] Part 1: Liquid Glass theme (QA'd in headless Chromium)
- [x] Part 2: text markup (highlight/underline/strikeout/squiggly), selection popup, click-to-edit, markup toolbar
- [~] Part 3: MULTI-PDF + SPLIT VIEW. 3a tabs DONE; 3b split view DONE (incl. linked scroll = 3c); verified in browser
- [x] Part 3d: PDF Compare (visual + text diff) DONE, verified in browser
- [~] Part 4: (4a hub, 4b PDF->images/text, 4c images->PDF DONE; 4d split/extract/compress/crop, 4e presets+batch, 4f LibreOffice bridge TODO) TOOLS HUB (Xodo-style gallery: Convert from PDF / Convert to PDF / Organize / Edit / View, glass cards, drop zone) + Convert & Export engines, inspired by Filee (see mapping below). Hub = front door: File > Tools or Mod+K style entry; cards open the right modal/command
- [ ] Part 5: Draw tools (pen, eraser, shapes, arrows, stamps, free-text)
- [ ] Part 6: Document tools (user bookmarks, extract/split UI, crop, recent files)
- [ ] Part 7: Reading modes (sepia), password protect/unlock, redaction
- [ ] Part 8: Tests, a11y, perf, docs

### Filee -> Folio mapping (Filee is a C#/Avalonia desktop converter; we re-implement ideas, not code)
Browser-capable (do these, pure TS, offline): PDF -> PNG/JPG/WEBP (DPI + page range presets), PDF -> TXT/Markdown, images (JPG/PNG/WEBP/BMP/GIF) -> PDF, merge, split by ranges / every N pages / each page, extract pages, compress (rasterise-to-JPEG quality preset), batch queue with parallel progress, drag-drop "drop zone", output naming template, presets saved in localStorage, recent conversions list, result as zip (fflate) for many files.
Desktop-only (Tauri shell-out to optional engines, later, behind feature flag): DOCX/XLSX/PPTX/HWP -> PDF (LibreOffice), PDF -> DOCX, video/audio, CAD, e-books, archives, watch folders, right-click shell menu, CLI. Say so honestly in the UI (disabled with tooltip in browser).
Filee UX ideas to copy: quick-convert "donut"/radial menu at cursor (optional, Part 4c), one preset toolbar per file type, profiles.

### Part 3 design (decided in session 6)
- Folio is single-document (useDocumentStore etc. used by 45 files), so tabs are a SESSION SWITCHER: src/features/workspace/ keeps per-tab {original bytes copy, name, path, page, scale, fitMode}; switching reloads via loadSource and restores viewer state. Hooks via src/state/documentHooks.ts (onDocumentLoaded/onDocumentClosed) so state/actions.ts doesn't import the feature.
- pdf.js DETACHES the byte array on load: always .slice() before loadDocument.
- Dirty docs (docVersion>0 or pending edits): snapshot via engine.saveDocument() on switch-away. RISK: fingerprint may change -> per-fingerprint stores (annotations, signatures, edits, ocr) would not find their data. Test this; if it breaks, migrate localStorage keys old->new fingerprint.
- Split view: a SECOND PdfJsEngine instance (class, not the getEngine() singleton) in a read-only pane with its own page/zoom, showing any open tab; optional synchronized scrolling. Annotation editing stays on the main pane only.

## Part 3b/3c status (split view + linked scroll) - session 7: DONE, verified in headless Chromium
Done (src/features/split/): store.ts (open, source tab|file, orientation columns|rows, ratio 0.2-0.8, sync; layout persisted in localStorage folio.split; scrollFraction helpers), SplitPane.tsx (own `new PdfJsEngine()` instance, lazy canvas+text layer pages, doc picker = any open tab / "this document" / open another file, page nav, zoom + fit width, link-scroll toggle, orientation toggle, close; draws main-view markups when the pane shows the active document), SplitArea.tsx (workarea wrapper + draggable/keyboard-resizable divider, role=separator), commands.ts (view.toggleSplit = Mod+Alt+S; opens on the other tab, else same doc), View menu 'Split view', icons split-columns/split-rows/link, glass + base CSS. Mounted in App.tsx (<SplitArea> wraps <main>).
Bug fixed on the way: PdfViewer attached its ResizeObserver at mount when the scroller did not exist, so fit-width never re-fit when the area resized -> effect now re-runs on `status`.
Tests: split store + scroll maths (4). Full suite 832 passed; eslint, typecheck, build pass. Browser script: screenshot-tools/split.mjs (+mklong.mjs makes long test PDFs).
Verified: opens on other tab; keyboard divider resize; stacked layout; linked scrolling both directions; same-doc pane shows highlight; closes cleanly.
Limits: second pane is read-only (no annotation editing, forms, search highlights); text selection works but the markup popup does not appear there; split state is not restored on restart; pane re-loads when its source tab changes but is not live-updated while the other doc is edited (reopen the pane or re-pick it). Pane wraps controls on narrow widths.
NEXT: Part 4 Convert & Export center (Filee-inspired). Start by reading src/features/export/ (saveDocument.ts), src/features/combine/ (modal + store patterns), src/features/pageops/operations.ts (pdf-lib page ops) and check package.json for fflate/jszip. Plan: src/features/convert/ with (1) task model + queue store with parallel progress, (2) engines in pure TS: pdfToImages (renderPageToImage/canvas -> png/jpg/webp, DPI + page range), pdfToText/Markdown, imagesToPdf (pdf-lib embedPng/embedJpg), splitPdf (ranges / every N / each page), compressPdf (rasterise-to-JPEG preset), zip via fflate, (3) a glass 'Convert & Export' modal (File menu) with drop zone, preset chips, output naming template, results list + download/zip, (4) commands + tests + browser test script. Desktop-only formats (DOCX/PPTX/HWP/video) shown disabled with a tooltip.

## Part 3a status (document tabs) - session 6: DONE, verified in headless Chromium
Done:
- src/state/documentHooks.ts: onBeforeDocumentLoad / onDocumentLoaded / onDocumentClosed. state/actions.ts emits them (loadSourceHoldingLock copies bytes with .slice() BEFORE pdf.js detaches them; closeDocument emits closed).
- src/features/workspace/: store.ts (Tab {id,name,fingerprint,data,url,path,edited,page,scale,fitMode}, neighbourAfterClose), actions.ts (registerWorkspace, switchTab, closeTab, cycleTab, captureActive), TabBar.tsx (ARIA tabs; arrows/Home/End/Delete; middle-click closes; shows when >=2 tabs; + opens another), commands.ts (tabs.next/prev = Mod+Alt+Right/Left), index.ts. Mounted in App.tsx under Toolbar; File menu has Next/Previous document; glass + base CSS added.
- Edited tabs: on switch-away, bytes are re-saved via engine.saveDocument() if docVersion>0 or pending edits; on switch-back the ORIGINAL fingerprint is restored and annotation/signature/edit/ocr stores reload under it (switchTab). VERIFIED: rotate page + underline, switch to other tab and back -> rotation and underline both kept.
- Bugs found+fixed by browser tests: (1) closing the active tab silently failed because the neighbour switch ran while closeDocument still held the document lock -> deferred with setTimeout 0; (2) sidebar thumbnails showed the previous doc when page counts matched -> Thumbnails PageList keyed by fingerprint.
- Tests: workspace store (4). Full suite 828 passed; eslint, typecheck, vite build pass. Browser scripts: screenshot-tools/tabs.mjs, edited.mjs.
Limits: tabs are not persisted across app restarts; large PDFs are held in memory once per tab (original bytes copy); URL-backed tabs reload from the URL; Word Count toast shows on each switch (plugin, harmless).
NEXT: 3b split view. Plan: src/features/split/ with SplitPane = second PdfJsEngine instance (new PdfJsEngine(), NOT getEngine()), read-only canvas+text-layer pages with own page/zoom, a tab picker (any open workspace tab, or open file), draggable divider, vertical/horizontal layout toggle, View menu 'Split view' (Mod+Alt+S?), 3c synchronized scrolling toggle. Check how Page.tsx renders (engine calls: renderPage, renderTextLayer) and reuse where possible; the main pane keeps full editing.

## Visual QA - session 5 (headless Chromium screenshots) DONE for Parts 1-2
Method: `npm i @sparticuz/chromium playwright-core` in /tmp (bundled Chromium; works in the sandbox, plain playwright download does not). Scripts saved in screenshot-tools/ (lib.mjs launch/openPdf/selectPhrase, shots.mjs full tour, mkpdf.mjs sample PDF). Run: start `npx vite --port 5199 --host 127.0.0.1` detached with setsid, then `node shots.mjs`. Ctrl+O + filechooser opens the PDF; click the page first for focus.
Bugs found and fixed from the screenshots:
1. Menus clipped under toolbar/sidebar: backdrop-filter made each bar a stacking context. Fix: glass.css menubar z-index 50, toolbar 45.
2. Dark-mode highlights vanished (multiply blend on black page). Fix: mix-blend-mode: screen under [data-theme='dark'].
3. Annotations panel row cramped by the new type label. Fix: kind stacked above text (.folio-annotations-list__body), page number flex none.
4. "Combine PDFs..." wrapped in the pill button. Fix: nowrap + smaller padding in glass.css.
Still unchecked: mobile/narrow layout and drawer, Organize Pages / signature / About dialogs under glass, high-contrast mode, e2e suite (npm run e2e).
LESSON: always screenshot after UI work; the earlier "code complete" claims missed bug 1 and 2.

## Part 2b status (click-to-edit + persistent toolbar) - session 4: CODE COMPLETE, NEEDS VISUAL QA
Done (all in src/features/annotations/):
- hitTest.ts (+test): hitMarkup() on stored rects; no DOM layer added, so text selection is untouched.
- MarkupEditPopup.tsx: document click -> hit-test -> popup above the markup with color swatches, highlight opacity, delete. Esc/scroll/resize/outside click closes. Uses store.recolor/remove.
- markupUi.ts: toolbarOpen, tool, toggleToolbar (also disarms tool), setTool.
- MarkupToolbar.tsx: bottom-centre glass pill, 4 toggle tools (aria-pressed), colors, opacity, close; Esc disarms. While a tool is armed, MarkupPopup auto-applies on selection instead of showing.
- MarkupControls.tsx (shared swatches/slider), keepSelection.ts (preventDefault on mousedown except range inputs, which fixed the opacity slider being undraggable in 2a).
- Command annotate.toolbar (Mod+Shift+A) + Annotate menu entry "Markup toolbar". Mounted in App.tsx.
- 824 tests pass; typecheck, eslint, vite build pass.
Known limits: not visually verified (no browser in sandbox); no component tests for the three popups (jsdom lacks layout); no undo for markup edits; markup toolbar has no toolbar-bar button (menu/shortcut only).

## Part 2a status (text markup) - session 3: CODE COMPLETE, NEEDS VISUAL QA
- types.ts: TextMarkupType (highlight|underline|strikeout|squiggly), isTextMarkup, ANNOTATION_LABELS, MARKUP_SWATCHES (6), markupColor() (only highlight translucent), parseRgba().
- store.ts: addMarkup(type,...), recolor(id,color). commands.ts: addMarkupFromSelection, currentMarkupColor; commands annotate.underline (Mod+Shift+U), annotate.strikeout (Mod+Shift+X), annotate.squiggly.
- AnnotationLayer renders all 4 via CSS (global.css .folio-annotation--*; squiggly uses a CSS mask); bake.ts writes /Highlight /Underline /StrikeOut /Squiggly.
- MarkupPopup.tsx: glass bar above a text selection (4 markups, comment, colors, opacity). Panel shows type label; Annotate + context menus; icons underline/strikethrough/squiggly.

## Part 1 status (Liquid Glass) - sessions 1-2: CODE COMPLETE, NEEDS VISUAL QA
Done:
- src/theme/glass.css: all rules scoped under :root[data-style='glass']. Translucent tokens, ambient gradient, backdrop blur on toolbar/menubar/sidebar/menus/context menu/search/modal/dropdown/note editor/empty card, floating rounded toolbar + sidebar (margins only), pill buttons, glass inputs, fallbacks (prefers-reduced-transparency, forced-colors incl. token re-assert).
- themeStore: `style: 'glass' | 'classic'` (key folio.style, default glass), setStyle, STYLE_LABELS (+ test).
- ThemeProvider + index.html bootstrap set data-style on <html>.
- AppearanceMenu: new "Interface style" radio group (Liquid Glass / Classic).
- docs/theming.md and CHANGELOG.md updated. typecheck, eslint, vitest (theme+toolbar), vite build pass.
Known limits / TODO for QA (no browser was available in the sandbox, so NOT visually verified):
- Check light + dark in a real browser: contrast of muted text over glass (WCAG AA), blur strength, sidebar margin vs thumbnails, mobile drawer (.folio-sidebar-backdrop), Organize Pages modal, signature dialog, About dialog, comment popovers.
- Run e2e (npm run e2e) if possible: floating toolbar margins may shift Playwright coordinates.
Next: start Part 2 (Xodo-style annotation toolbar). Look at src/features/annotations/, src/commands/, src/components/Toolbar/toolbarTools.ts, src/state/ first.

## (old note) draw tools plan, now Part 5
Start by reading src/features/placement/ (PlacementLayer), src/features/editing/ (EditLayer: text boxes), src/features/signatures/ (SignatureLayer) and src/features/annotations/bake.ts to see how page overlays are stored, rendered and baked into the PDF. Plan: new feature folder src/features/draw/ with (1) ink store (strokes as normalized points per page, persisted like annotations), (2) pointer-driven DrawLayer with pen/eraser + color/width, (3) shapes (rect, ellipse, line, arrow) , (4) bake as /Ink and /Square /Circle /Line annotations via pdf-lib, (5) toolbar UI reusing the glass markup-popup styles, (6) free-text box if not already covered by Add text box. Add Mod+Shift shortcuts only if free (taken: H M U X A S L).

## HOW TO RESUME (the assistant cannot schedule itself; the USER must start the next session)
1. New chat: attach PROGRESS.md and say "Continue the Folio project from PROGRESS.md".
2. The sandbox may be wiped between sessions. Rebuild: `git clone https://github.com/owenpkent/folio.git && cd folio`, then `git am` ALL patches 0001..000N in order (kept in outputs/), `npm ci`, then continue. screenshot-tools/ has the headless-browser scripts (see 'Visual QA').
3. Order of work after 3b: 3d PDF Compare -> Part 4 Tools hub + convert engines -> Part 5 draw tools -> Part 6 crop/compress/flatten/split UI/bookmarks -> Part 7 redact/password/reading modes -> Part 8 polish.
