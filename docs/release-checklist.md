# Vnyl release checklist

- [ ] Update the version consistently in `package.json`, `src-tauri/Cargo.toml`,
      and `src-tauri/tauri.conf.json`.
- [ ] Review the release notes and confirm the app metadata identifies Vnyl.
- [ ] Build the Windows installers with `npm run tauri:build:local`.
- [ ] Install the setup executable and smoke-check opening, editing, saving,
      printing, and file association behavior.
- [ ] Sign the public release installer with a Vnyl-owned code-signing
      certificate before publishing.
- [ ] Upload the installers and release notes to
      `Horens025/Vnyl-PDF-desktop-application`.
- [ ] Generate update artifacts with the Vnyl signing key; publish the setup
      executable, its `.sig`, and `latest.json` to the Vnyl release.
