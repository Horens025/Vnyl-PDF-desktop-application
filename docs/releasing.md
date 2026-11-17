# Releasing Vnyl

Vnyl installers are built and published from the Vnyl repository:
https://github.com/Horens025/Vnyl-PDF-desktop-application

## Build Windows installers

From the repository root on Windows:

```powershell
npm ci
npm run tauri:build:local
```

The build creates the NSIS setup executable and MSI under
`src-tauri/target/release/bundle/`. The local build is unsigned; Windows may
show a SmartScreen warning. For public distribution, sign the installer with a
certificate issued for Vnyl before uploading it to a GitHub Release.

## Automatic updates

The app checks Vnyl's GitHub Releases feed and verifies updates with Vnyl's
public signing key in `src-tauri/tauri.conf.json`. The matching private key is
stored locally at `.tauri/vnyl.key`; `.tauri/` is ignored by Git. Keep that file
backed up securely and never upload it to the repository.

For an update release, build with `TAURI_SIGNING_PRIVATE_KEY` set to the path
of `.tauri/vnyl.key`, then generate `latest.json` with:

```powershell
$env:TAURI_SIGNING_PRIVATE_KEY = (Resolve-Path .tauri/vnyl.key).Path
npm run tauri build
npm run release:manifest -- --version 0.8.0 --notes "Release notes"
```

Upload the setup executable, its `.sig` file, and `release/latest.json` to the
same Vnyl GitHub Release. The generated key has no password, so protect the
local key file and add it to a private backup.
