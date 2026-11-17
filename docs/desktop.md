# Vnyl as a desktop application

Vnyl is a desktop app first: a Tauri 2 shell (Rust) around the React interface.
The browser build exists, but every feature is designed for the desktop app, and
desktop-only integrations (native dialogs, files on disk, LibreOffice) are the
long-term home of anything a browser cannot do safely.

## Build and run

Prerequisites: Node.js 22.22.2+, a stable Rust toolchain, and the platform's webview
dependencies ([Tauri prerequisites](https://tauri.app/start/prerequisites/)):

| Platform | Needs |
| --- | --- |
| Windows | Microsoft C++ Build Tools, WebView2 (preinstalled on Windows 11) |
| macOS | Xcode Command Line Tools |
| Linux | `libwebkit2gtk-4.1-dev`, `libgtk-3-dev`, `libssl-dev`, `libayatana-appindicator3-dev`, `librsvg2-dev` |

```bash
npm ci
npm run tauri dev        # the desktop app with hot reload
npm run tauri build      # standard desktop bundle
npm run tauri:build:local # an unsigned installer for your own use; see INSTALL.md
```

CI builds the app on Windows, macOS and Linux for every change (`ci.yml`).

## How the desktop app differs from the browser build

| | Desktop | Browser |
| --- | --- | --- |
| Open a file | native picker, "Open with", drag onto the app icon | file input, drag and drop |
| Save a PDF | native Save dialog, atomic write (`write_document`, `.pdf` only) | download |
| Save converter output | native Save dialog or **Save all to folder** (`write_export`, closed list of file types) | download |
| Office formats | planned: LibreOffice on the computer | not available |

### Writing files

The webview never gets a general file-write permission. Two narrow Rust commands
write files, both to a path the user just chose in a native dialog, both through
a temporary file and an atomic rename:

* `write_document`: `.pdf` only.
* `write_export`: `png jpg jpeg webp zip txt md html` only. The list lives in
  `EXPORT_EXTENSIONS` in `src-tauri/src/lib.rs`; extend it deliberately, never
  with anything executable or scriptable.

## App icon

`scripts/make-icon.py` draws the icon; `npm run tauri icon
src-tauri/icons/source/vnyl-icon.png` turns it into every platform size.
