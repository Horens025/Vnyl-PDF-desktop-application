; Vnyl NSIS installer hooks (wired via tauri.conf.json -> bundle.windows.nsis.installerHooks).
;
; Everything here exists to make Vnyl *selectable* as the Windows handler for
; `.pdf`. It deliberately does not try to *become* the handler: since Windows 8
; the current default lives in
;
;   HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.pdf\UserChoice
;
; whose `ProgId` value is protected by a per-user, per-extension hash that only
; Explorer can produce. An installer that writes it is (rightly) treated as
; hijacking, and Windows resets the association. So a machine with Acrobat or
; Edge already owning `.pdf` keeps it until the *user* changes it, and every
; hand-off that goes through ShellExecute -- double-click, "Open with" defaults,
; and Chrome's "open downloaded file" / "always open with system viewer" --
; follows UserChoice, not us.
;
; That guarantee has one gap: `bundle.fileAssociations` (via Tauri's own
; APP_ASSOCIATE) unconditionally overwrites the *non-UserChoice* default at
; `Software\Classes\.pdf`. Precedence is UserChoice, then HKCU\Software\Classes,
; then HKLM\Software\Classes, so on a machine where the previous handler only
; ever registered in HKLM and the user never made an explicit choice (a fresh
; image, a server SKU, anything that has cleared FileExts), installing Vnyl
; DOES take `.pdf` over. See docs/getting-started.md for the user-facing note.
;
; What we can do is make that user choice possible and obvious. Tauri's
; `bundle.fileAssociations` (see tauri.conf.json) only writes the ProgID itself
; plus the `.pdf` default value; on its own that leaves Vnyl missing from the
; "Open with" list and showing up in Settings under its *file-type* description
; rather than its name. The numbered blocks below close those gaps (and fix a
; correctness bug in Tauri's own output).
;
; Per-user install -> SHCTX resolves to HKCU (Tauri sets it from the install
; mode; this matches what its own APP_ASSOCIATE macro writes). ${MAINBINARYNAME}
; is "vnyl" and $INSTDIR the install root, both defined by the generated script
; that includes this file. `installMode` is pinned to "currentUser" in
; tauri.conf.json rather than left at its default, because
; `registeredAppUser=Vnyl` in src-tauri/src/lib.rs only resolves against
; HKCU\Software\RegisteredApplications: a `perMachine` or `both` build would
; move this key to HKLM and silently break that deep link.

!define VNYL_PROGID "PDF Document" ; must match bundle.fileAssociations[].name
; Mirrors bundle.shortDescription in tauri.conf.json (source of truth -- NSIS
; cannot read JSON, so this copy has to be kept in sync by hand). Also
; duplicated as `description` in src-tauri/Cargo.toml.
!define VNYL_DESCRIPTION "Read, mark up and convert PDFs with Vnyl"

!macro NSIS_HOOK_POSTINSTALL
  ; 1. Registered application -> Vnyl gets its own page in Settings > Default
  ;    apps, which the in-app "Set as default PDF viewer" action deep-links to
  ;    via `ms-settings:defaultapps?registeredAppUser=Vnyl`.
  WriteRegStr SHCTX "Software\${PRODUCTNAME}\Capabilities" "ApplicationName" "${PRODUCTNAME}"
  WriteRegStr SHCTX "Software\${PRODUCTNAME}\Capabilities" "ApplicationDescription" "${VNYL_DESCRIPTION}"
  WriteRegStr SHCTX "Software\${PRODUCTNAME}\Capabilities" "ApplicationIcon" "$INSTDIR\${MAINBINARYNAME}.exe,0"
  WriteRegStr SHCTX "Software\${PRODUCTNAME}\Capabilities\FileAssociations" ".pdf" "${VNYL_PROGID}"
  ; Advertise the vnyl:// deep-link scheme too (registered by Tauri's own
  ; generated script from plugins.deep-link in tauri.conf.json), so Settings >
  ; Default apps has somewhere to show and repair it, the same as .pdf.
  WriteRegStr SHCTX "Software\${PRODUCTNAME}\Capabilities\URLAssociations" "vnyl" "vnyl"
  ; The value NAME here (not its data) has to be the literal "Vnyl":
  ; src-tauri/src/lib.rs hardcodes `registeredAppUser=Vnyl` in its deep link,
  ; so this stays a literal instead of ${PRODUCTNAME} -- a product rename
  ; should not silently desync it from that Rust string.
  WriteRegStr SHCTX "Software\RegisteredApplications" "Vnyl" "Software\${PRODUCTNAME}\Capabilities"

  ; 2. Advertise the ProgID against `.pdf` so Vnyl appears in Explorer's
  ;    "Open with" / "Choose another app" list -- the shortest route a user has
  ;    to flipping UserChoice, and the one they reach from a downloaded file.
  ;    Tauri's APP_ASSOCIATE writes the `.pdf` *default* value but never this
  ;    subkey, so without it Vnyl is absent from the picker entirely.
  WriteRegStr SHCTX "Software\Classes\.pdf\OpenWithProgids" "${VNYL_PROGID}" ""

  ; 3. Name the app, not the format. The ProgID's own default value is its file
  ;    *type* description ("Portable Document Format document"), which is what
  ;    the picker and Settings would otherwise label the entry -- unrecognisable
  ;    as Vnyl. An Application subkey overrides that with the app's identity.
  WriteRegStr SHCTX "Software\Classes\${VNYL_PROGID}\Application" "ApplicationName" "${PRODUCTNAME}"
  WriteRegStr SHCTX "Software\Classes\${VNYL_PROGID}\Application" "ApplicationDescription" "${VNYL_DESCRIPTION}"
  WriteRegStr SHCTX "Software\Classes\${VNYL_PROGID}\Application" "ApplicationIcon" "$INSTDIR\${MAINBINARYNAME}.exe,0"

  ; Also fix a bug in Tauri's own APP_ASSOCIATE (installer.nsi): the shell\open
  ; command it writes for this ProgID is unquoted -- "$INSTDIR\vnyl.exe $\"%1$\""
  ; -- so an install path containing a space (a custom directory, or a profile
  ; like "C:\Users\John Smith") makes ShellExecute resolve the bare exe token at
  ; the first space instead of the real path (CWE-428). This hook runs after
  ; APP_ASSOCIATE, so overriding it here is enough; use the same quoted form
  ; already used below for Applications\${MAINBINARYNAME}.exe.
  WriteRegStr SHCTX "Software\Classes\${VNYL_PROGID}\shell\open\command" "" "$\"$INSTDIR\${MAINBINARYNAME}.exe$\" $\"%1$\""

  ; 4. The other half of the picker. Explorer builds "Open with" from both the
  ;    extension's OpenWithProgids and the per-executable Applications key; the
  ;    latter is also what "Look for another app on this PC" binds to, and what
  ;    supplies the friendly name when a user browses to vnyl.exe by hand.
  ;    SupportedTypes keeps Vnyl out of the picker for non-PDF files. Icon path
  ;    is left unquoted, matching Tauri's own APP_ASSOCIATE ICON argument above.
  WriteRegStr SHCTX "Software\Classes\Applications\${MAINBINARYNAME}.exe" "FriendlyAppName" "${PRODUCTNAME}"
  WriteRegStr SHCTX "Software\Classes\Applications\${MAINBINARYNAME}.exe\DefaultIcon" "" "$INSTDIR\${MAINBINARYNAME}.exe,0"
  WriteRegStr SHCTX "Software\Classes\Applications\${MAINBINARYNAME}.exe\shell\open" "FriendlyAppName" "${PRODUCTNAME}"
  WriteRegStr SHCTX "Software\Classes\Applications\${MAINBINARYNAME}.exe\shell\open\command" "" "$\"$INSTDIR\${MAINBINARYNAME}.exe$\" $\"%1$\""
  WriteRegStr SHCTX "Software\Classes\Applications\${MAINBINARYNAME}.exe\SupportedTypes" ".pdf" ""

  ; Explorer caches associations per session; broadcast the change so it shows
  ; up in "Open with" immediately instead of after the next shell restart.
  ; UPDATEFILEASSOC (from FileAssociation.nsh, included ahead of this file)
  ; passes SHCNF_FLUSH, which makes the broadcast synchronous -- plain
  ; SHChangeNotify with no flags queues it instead, and the installer's own
  ; exit can race the shell draining that queue.
  !insertmacro UPDATEFILEASSOC
!macroend

!macro NSIS_HOOK_POSTUNINSTALL
  DeleteRegValue SHCTX "Software\RegisteredApplications" "Vnyl"
  ; Software\Vnyl and Tauri's own Software\vnyl\Vnyl (which stores $INSTDIR
  ; for RestorePreviousInstallLocation) are the same registry key -- names are
  ; case-insensitive -- so only remove our own Capabilities subtree here, never
  ; the parent wholesale. `/ifempty` then only takes the parent if Tauri's own
  ; guarded block above (gated on the "delete app data" checkbox) also cleared
  ; its sibling.
  DeleteRegKey SHCTX "Software\${PRODUCTNAME}\Capabilities"
  DeleteRegKey /ifempty SHCTX "Software\${PRODUCTNAME}"

  ; Only our own value: OpenWithProgids is shared, and every other value in it
  ; belongs to a different application. (`DeleteRegKey /ifempty` would not do
  ; here -- it keys off subkeys, not values, so it would take the siblings with
  ; it.) An empty leftover key is harmless.
  DeleteRegValue SHCTX "Software\Classes\.pdf\OpenWithProgids" "${VNYL_PROGID}"

  ; APP_ASSOCIATE (installer.nsi) backs up the previous `.pdf` default on every
  ; install, including a reinstall over an existing Vnyl install. When that
  ; happens the backup itself holds our own ProgID, so APP_UNASSOCIATE, which
  ; ran just before this hook, restored `.pdf`'s default to a ProgID it deleted
  ; a moment earlier, leaving it dangling. Clean that up, plus the now-useless
  ; backup value.
  ReadRegStr $R0 SHCTX "Software\Classes\.pdf" ""
  ${If} $R0 == "${VNYL_PROGID}"
    DeleteRegValue SHCTX "Software\Classes\.pdf" ""
  ${EndIf}
  DeleteRegValue SHCTX "Software\Classes\.pdf" "${VNYL_PROGID}_backup"

  ; If the user picked Vnyl via "Open with > Always", Explorer wrote this
  ; ProgId under a hash only Explorer can produce. We cannot rewrite that
  ; value, but deleting the key is allowed: Windows falls back to the `.pdf`
  ; default restored above and re-prompts next time the user picks a handler.
  ; Without this, "How do you want to open this file?" never goes away.
  ReadRegStr $R0 HKCU "Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.pdf\UserChoice" "ProgId"
  ${If} $R0 == "${VNYL_PROGID}"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.pdf\UserChoice"
  ${EndIf}

  ; Software\Classes\${VNYL_PROGID}\Application (written above) is not deleted
  ; here: APP_UNASSOCIATE already removed the whole Software\Classes\PDF Document
  ; key, including that subkey, before this hook runs.
  DeleteRegKey SHCTX "Software\Classes\Applications\${MAINBINARYNAME}.exe"
  !insertmacro UPDATEFILEASSOC
!macroend
