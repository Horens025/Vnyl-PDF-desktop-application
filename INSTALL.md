# Installing Vnyl on your computer

Vnyl is built from source into a normal desktop app. The first build takes
10-20 minutes (it compiles the Rust shell once); after that, rebuilds are fast.
You only need to do this once.

> There is no pre-built installer yet. The official release workflow signs
> installers with the maintainer's certificate, so a personal build uses the
> settings in `src-tauri/tauri.local.conf.json`, which switch that off.

## Windows 10 / 11

1. **Install the tools** (open *Windows Terminal* or *PowerShell*; accept any prompts):

   ```powershell
   winget install OpenJS.NodeJS.LTS
   winget install Rustlang.Rustup
   winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
   winget install Microsoft.EdgeWebView2Runtime
   ```

   Then **close and reopen** the terminal, and run `rustup default stable`.
   (Windows 11 already has WebView2; the last line is harmless there.)

2. **Unzip** `vnyl-0.8.0-source.zip`, open the `vnyl` folder, click the address
   bar in File Explorer, type `powershell` and press Enter.

3. **Build the installer:**

   ```powershell
   npm ci
   npm run tauri:build:local -- --bundles nsis
   ```

4. **Install it.** Open `src-tauri\target\release\bundle\nsis\` and run
   `Vnyl_0.8.0_x64-setup.exe`. It installs for your user only (no administrator
   rights needed) and adds Vnyl to the Start menu. Windows SmartScreen may warn
   that the app is unsigned: choose **More info > Run anyway**.

## macOS

```bash
xcode-select --install
brew install node
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh   # then restart the terminal
cd vnyl
npm ci
npm run tauri:build:local
```

Open `src-tauri/target/release/bundle/dmg/`, drag **Vnyl** into Applications. The
first time, right-click the app and choose **Open** (it is not notarized).

## Linux (Debian / Ubuntu)

```bash
sudo apt install libwebkit2gtk-4.1-dev libgtk-3-dev libssl-dev \
  libayatana-appindicator3-dev librsvg2-dev build-essential curl
# Node 20+ and Rust (https://rustup.rs) are also required
cd vnyl
npm ci
npm run tauri:build:local
```

Install the `.deb` or run the `.AppImage` from `src-tauri/target/release/bundle/`.

## Just want to try it first?

You can run the app without building an installer. With Node and Rust installed:

```bash
cd vnyl
npm ci
npm run tauri dev
```

This opens the real desktop window straight away (the first run compiles for a
few minutes). Close the window to stop it.

If you only have Node (no Rust), `npm ci && npm run dev` serves the interface in
your browser at <http://localhost:1420>. Most features work, but saving files uses
browser downloads instead of the system Save dialog.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `npm` or `cargo` is not recognized | Close and reopen the terminal after installing |
| `link.exe not found` (Windows) | The C++ Build Tools step did not finish; rerun it and tick *Desktop development with C++* |
| The build asks for a signing key or a certificate | You used `npm run tauri build`; use `npm run tauri:build:local` instead |
| A blank white window (Windows 10) | Install the WebView2 runtime (step 1) |
| Antivirus quarantines the build | Allow the `src-tauri\target` folder, then rebuild |
