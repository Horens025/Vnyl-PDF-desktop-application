package app.vnyl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import app.vnyl.glass.GlassActionPill
import app.vnyl.glass.GlassPalette
import app.vnyl.glass.LiquidGlassShaders
import app.vnyl.glass.LiquidGlassSurface
import app.vnyl.glass.LocalWindowPointer
import app.vnyl.glass.MorphingSegmentedControl
import app.vnyl.glass.motionSpring
import app.vnyl.glass.resolveGlassPalette
import app.vnyl.native.DocxDocumentInfo
import app.vnyl.native.OutlineItemInfo
import app.vnyl.state.DocumentKind
import app.vnyl.state.PdfTabState
import app.vnyl.state.SidebarTab
import app.vnyl.state.ToolMode
import app.vnyl.state.UiStyle
import app.vnyl.state.UiTheme
import app.vnyl.state.VNYL_SWATCHES
import app.vnyl.state.VnylStore
import app.vnyl.state.ZoomMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.io.File
import kotlin.math.roundToInt

interface WindowActions {
    fun openPdf()
    fun pickPdfFile(title: String): File?
    fun pickP12File(): File?
    fun pickSignedPdfDest(defaultName: String): File?
    fun savePdf(saveAs: Boolean)
    fun mergePdf()
    fun exportPng()
    fun exportText(markdown: Boolean)
    fun exportDocx()
    fun exportWordAsPdf(doc: DocxDocumentInfo)
    fun pickExtractFile(pagesOneBased: String)
    fun pickSplitFolder(chunkSize: Int)
    fun insertImage()
    fun minimize()
    fun toggleMaximize()
    fun close()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.VnylApp(
    store: VnylStore,
    actions: WindowActions,
) {
    val isDark = store.isDarkUi
    val palette = resolveGlassPalette(
        isDark = isDark,
        style = store.uiStyle,
        glassLook = store.glassLook,
        refractionScale = store.refractionStrength,
    )
    val tab = store.activeTab

    var pointerPos by remember { mutableStateOf(Offset(-2000f, -2000f)) }

    val commands = remember(
        store.activeTabIndex,
        store.sidebarOpen,
        store.splitViewEnabled,
        store.twoPageSpread,
        store.readingMode,
        store.autoScrollActive,
    ) {
        listOf(
            AppCommand("open", "File: Open PDF or Word (.docx/.doc)…", "Ctrl+O") { actions.openPdf() },
            AppCommand("new_word", "Word: New Blank Word Document (.docx)", "") { store.newBlankDocx() },
            AppCommand("save", "File: Save", "Ctrl+S", requiresDoc = true) { actions.savePdf(false) },
            AppCommand("save_as", "File: Save As…", "Ctrl+Shift+S", requiresDoc = true) { actions.savePdf(true) },
            AppCommand("print", "File: Print…", "Ctrl+P", requiresDoc = true) { store.printDocument() },
            AppCommand("undo", "Edit: Undo", "Ctrl+Z", requiresDoc = true) { store.undo() },
            AppCommand("redo", "Edit: Redo", "Ctrl+Y", requiresDoc = true) { store.redo() },
            AppCommand("word_to_pdf", "Word: Convert Active .docx to PDF Tab", "") { store.convertActiveDocxToPdfTab() },
            AppCommand("export_docx", "Convert: Export PDF to Word (.docx)…", "", requiresDoc = true) { actions.exportDocx() },
            AppCommand("organizer", "Organize: Page Organizer…", "", requiresDoc = true) { store.organizerOpen = true },
            AppCommand("combine_pdfs", "Organize: Combine Multiple PDFs…", "") { store.combineModalOpen = true },
            AppCommand("split_pdf", "Organize: Split / Extract PDF…", "", requiresDoc = true) { store.splitModalOpen = true },
            AppCommand("merge", "Organize: Merge Another PDF…", "", requiresDoc = true) { actions.mergePdf() },
            AppCommand("sign", "Annotate: Sign Document…", "", requiresDoc = true) { store.signatureModalOpen = true },
            AppCommand("check_stamp", "Annotate: Place Checkmark (✓)", "", requiresDoc = true) { store.prepareSymbolStamp(true) },
            AppCommand("cross_stamp", "Annotate: Place Cross (✗)", "", requiresDoc = true) { store.prepareSymbolStamp(false) },
            AppCommand("insert_img", "Annotate: Insert Image…", "", requiresDoc = true) { actions.insertImage() },
            AppCommand("keychain", "Security: Digital Signing & Identities (.p12)…", "") { store.keychainModalOpen = true },
            AppCommand("compare", "View: Compare Documents…", "", requiresDoc = true) { store.compareModalOpen = true },
            AppCommand("export_png", "Convert: Export Current Page as PNG…", "", requiresDoc = true) { actions.exportPng() },
            AppCommand("export_md", "Convert: Export Document to Markdown…", "", requiresDoc = true) { actions.exportText(true) },
            AppCommand("export_txt", "Convert: Export Document to Plain Text…", "", requiresDoc = true) { actions.exportText(false) },
            AppCommand("copy", "Edit: Copy Selected Text", "Ctrl+C", requiresDoc = true) { store.copySelectionToClipboard() },
            AppCommand("find", "Edit: Find in Document", "Ctrl+F", requiresDoc = true) { store.searchOpen = !store.searchOpen },
            AppCommand("rot_cw", "Organize: Rotate Page 90° Clockwise", "", requiresDoc = true) { store.rotateCurrentPage(1) },
            AppCommand("rot_ccw", "Organize: Rotate Page 90° Counter-Clockwise", "", requiresDoc = true) { store.rotateCurrentPage(-1) },
            AppCommand("del_page", "Organize: Delete Current Page", "", requiresDoc = true) { store.deleteCurrentPage() },
            AppCommand("spread", "View: Toggle Two-Page Book Spread", "", requiresDoc = true) { store.twoPageSpread = !store.twoPageSpread },
            AppCommand("split", "View: Toggle Split View", "Ctrl+\\", requiresDoc = true) { store.splitViewEnabled = !store.splitViewEnabled },
            AppCommand("sidebar", "View: Toggle Sidebar", "Ctrl+B", requiresDoc = true) { store.sidebarOpen = !store.sidebarOpen },
            AppCommand("reading", "View: Toggle Reading Mode", "F11", requiresDoc = true) { store.readingMode = !store.readingMode },
            AppCommand("autoscroll", "View: Toggle Auto-Scroll (Teleprompter)", "", requiresDoc = true) { store.autoScrollActive = !store.autoScrollActive },
            AppCommand("props", "Help: Document Properties…", "", requiresDoc = true) { store.openDocumentProperties() },
            AppCommand("theme", "Appearance: Toggle Light / Dark Theme", "") {
                store.uiTheme = if (store.isDarkUi) UiTheme.LIGHT else UiTheme.DARK
                store.persistPrefs()
            },
            AppCommand("shortcuts", "Help: Keyboard Shortcuts", "Ctrl+/") { store.shortcutsOpen = true },
        )
    }

    val sidebarInsetDp by animateDpAsState(
        targetValue = if (tab != null && tab.kind == DocumentKind.PDF && store.sidebarOpen && !store.readingMode) 264.dp else 0.dp,
        animationSpec = motionSpring(store.motionPreset),
        label = "sidebar_inset",
    )

    val appColorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentContent,
            surface = Color(0xFF16181E),
            surfaceContainer = Color(0xFF16181E),
            surfaceContainerLow = Color(0xFF13151A),
            surfaceContainerHigh = Color(0xFF1C1F27),
            onSurface = palette.text,
            onSurfaceVariant = palette.textMuted,
            outlineVariant = palette.border,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentContent,
            surface = Color(0xFFFCFCFD),
            surfaceContainer = Color(0xFFFCFCFD),
            surfaceContainerLow = Color(0xFFF6F7F9),
            surfaceContainerHigh = Color(0xFFF1F2F6),
            onSurface = palette.text,
            onSurfaceVariant = palette.textMuted,
            outlineVariant = palette.border,
        )
    }

    MaterialTheme(colorScheme = appColorScheme) {
        CompositionLocalProvider(
            LocalWindowPointer provides pointerPos,
            LocalTextStyle provides TextStyle(fontFamily = FontFamily.SansSerif),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Move) { ev ->
                    ev.changes.firstOrNull()?.position?.let { pointerPos = it }
                }
                .onPointerEvent(PointerEventType.Exit) {
                    pointerPos = Offset(-2000f, -2000f)
                },
        ) {
            AmbientBackdrop(isDark = isDark, isGlass = store.uiStyle == UiStyle.LIQUID_GLASS)

            if (tab == null) {
                StartScreen(store = store, palette = palette, actions = actions)
            } else if (tab.kind == DocumentKind.DOCX) {
                DocxEditorViewport(
                    store = store,
                    tab = tab,
                    palette = palette,
                    topChromePaddingDp = if (store.readingMode) 24 else 148,
                    onExportPdfFile = { doc -> actions.exportWordAsPdf(doc) },
                )
            } else if (store.splitViewEnabled) {
                val secondaryTab = store.tabs.getOrNull((store.activeTabIndex + 1) % store.tabs.size) ?: tab
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PdfViewport(
                            store = store,
                            tab = tab,
                            topChromePaddingDp = if (store.readingMode) 24 else 148,
                            leftInsetDp = sidebarInsetDp.value.roundToInt(),
                            isPrimary = true,
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(palette.border))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PdfViewport(
                            store = store,
                            tab = secondaryTab,
                            topChromePaddingDp = if (store.readingMode) 24 else 148,
                            leftInsetDp = 0,
                            isPrimary = false,
                        )
                    }
                }
            } else {
                PdfViewport(
                    store = store,
                    tab = tab,
                    topChromePaddingDp = if (store.readingMode) 24 else 148,
                    leftInsetDp = sidebarInsetDp.value.roundToInt(),
                    isPrimary = true,
                )
            }

            AnimatedVisibility(
                visible = !store.readingMode,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IntegratedTitleAndTabBar(store = store, palette = palette, actions = actions)
                    FloatingToolbar(store = store, tab = tab, palette = palette, actions = actions)
                }
            }

            AnimatedVisibility(
                visible = tab != null && tab.kind == DocumentKind.PDF && store.sidebarOpen && !store.readingMode,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 148.dp, bottom = 16.dp),
            ) {
                if (tab != null) {
                    SidebarDrawer(store = store, tab = tab, palette = palette)
                }
            }

            AnimatedVisibility(
                visible = tab != null && tab.kind == DocumentKind.PDF && store.searchOpen,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut() + slideOutVertically { -it / 3 },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 148.dp, end = 20.dp),
            ) {
                SearchOverlay(store = store, palette = palette)
            }

            AnimatedVisibility(
                visible = store.appearanceOpen,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut() + slideOutVertically { -it / 3 },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 148.dp, end = 20.dp),
            ) {
                AppearancePopover(store = store, palette = palette)
            }

            if (tab != null && tab.kind == DocumentKind.PDF) {
                BottomPageAndZoomPill(
                    store = store,
                    tab = tab,
                    palette = palette,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }

            if (store.readingMode) {
                LiquidGlassSurface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    cornerRadius = 999.dp,
                    isDark = isDark,
                    style = store.uiStyle,
                    glassLook = store.glassLook,
                    refractionScale = store.refractionStrength,
                ) {
                    GlassActionPill(onClick = { store.readingMode = false }, palette = palette, motionPreset = store.motionPreset) {
                        Text("Exit Reading Mode (Esc)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            store.statusMessage?.let { msg ->
                LiquidGlassSurface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    cornerRadius = 14.dp,
                    isDark = isDark,
                    style = store.uiStyle,
                    glassLook = store.glassLook,
                    strong = true,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(msg, color = if (store.statusIsError) palette.danger else palette.text, fontSize = 12.sp)
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = palette.textMuted,
                            modifier = Modifier.size(15.dp).clickable { store.statusMessage = null },
                        )
                    }
                }
            }

            if (store.contextMenu != null) {
                GlassContextMenu(store = store, palette = palette, onExportPng = actions::exportPng)
            }
            if (store.pendingNote != null) {
                NoteComposerModal(store = store, palette = palette)
            }
            if (store.organizerOpen) {
                PageOrganizerModal(store = store, palette = palette, onPickExtractFile = actions::pickExtractFile)
            }
            if (store.splitModalOpen) {
                SplitPdfModal(
                    store = store,
                    palette = palette,
                    onPickChunkDir = actions::pickSplitFolder,
                    onPickExtractFile = actions::pickExtractFile,
                )
            }
            if (store.signatureModalOpen) {
                SignatureModal(store = store, palette = palette, onUploadImageFile = actions::insertImage)
            }
            if (store.compareModalOpen) {
                CompareDocumentsModal(store = store, palette = palette)
            }
            if (store.propertiesModalOpen) {
                DocumentPropertiesModal(store = store, palette = palette)
            }
            if (store.combineModalOpen) {
                CombinePdfsModal(store = store, palette = palette) { actions.pickPdfFile("Add PDF to Combine") }
            }
            if (store.keychainModalOpen) {
                SigningIdentitiesModal(
                    store = store,
                    palette = palette,
                    onPickSignedPdfDest = actions::pickSignedPdfDest,
                    onPickP12 = actions::pickP12File,
                )
            }
            if (store.pendingFormEdit != null) {
                FormFieldEditModal(store = store, palette = palette)
            }
            if (store.pendingPasswordFile != null) {
                PasswordPromptModal(store = store, palette = palette)
            }
            if (store.pendingDirtyConfirm != null) {
                DirtyConfirmModal(store = store, palette = palette, onSaveActive = { actions.savePdf(false) })
            }
            if (store.commandPaletteOpen) {
                CommandPaletteModal(store = store, commands = commands, palette = palette)
            }
            if (store.shortcutsOpen) {
                ShortcutsModal(store = store, palette = palette)
            }
        }
    }
    }
}

@Composable
private fun WindowScope.IntegratedTitleAndTabBar(
    store: VnylStore,
    palette: GlassPalette,
    actions: WindowActions,
) {
    WindowDraggableArea {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "VNYL",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = palette.text,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                    )
                    Box(modifier = Modifier.width(1.dp).height(14.dp).background(palette.border))
                    Spacer(Modifier.width(4.dp))
                    MenuDropdownButton("File", store, palette) {
                        DropdownMenuItem(text = { Text("Open PDF or Word… (Ctrl+O)") }, onClick = { store.openMenuId = null; actions.openPdf() })
                        DropdownMenuItem(text = { Text("New Word Document (.docx)") }, onClick = { store.openMenuId = null; store.newBlankDocx() })
                        DropdownMenuItem(text = { Text("Save (Ctrl+S)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; actions.savePdf(false) })
                        DropdownMenuItem(text = { Text("Save As… (Ctrl+Shift+S)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; actions.savePdf(true) })
                        DropdownMenuItem(text = { Text("Print… (Ctrl+P)") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; store.printDocument() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Export PDF -> Word (.docx)…") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; actions.exportDocx() })
                        DropdownMenuItem(text = { Text("Export Word -> PDF…") }, enabled = store.activeTab?.kind == DocumentKind.DOCX, onClick = { store.openMenuId = null; store.activeTab?.docxDoc?.let { actions.exportWordAsPdf(it) } })
                        DropdownMenuItem(text = { Text("Export Page as PNG…") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; actions.exportPng() })
                        DropdownMenuItem(text = { Text("Export as Markdown…") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; actions.exportText(true) })
                        DropdownMenuItem(text = { Text("Export as Plain Text…") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; actions.exportText(false) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Close Tab (Ctrl+W)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.requestCloseTab(store.activeTabIndex) })
                    }
                    MenuDropdownButton("Edit", store, palette) {
                        DropdownMenuItem(text = { Text("Undo (Ctrl+Z)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.undo() })
                        DropdownMenuItem(text = { Text("Redo (Ctrl+Y)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.redo() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Copy Selected Text (Ctrl+C)") }, enabled = store.selection != null, onClick = { store.openMenuId = null; store.copySelectionToClipboard() })
                        DropdownMenuItem(text = { Text("Find in Document (Ctrl+F)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.searchOpen = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Sign Document…") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.signatureModalOpen = true })
                        DropdownMenuItem(text = { Text("Place Checkmark (✓)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.prepareSymbolStamp(true) })
                        DropdownMenuItem(text = { Text("Place Cross (✗)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.prepareSymbolStamp(false) })
                        DropdownMenuItem(text = { Text("Insert Image…") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; actions.insertImage() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Saved Signing Identities (.p12)…") }, onClick = { store.openMenuId = null; store.keychainModalOpen = true })
                    }
                    MenuDropdownButton("View", store, palette) {
                        DropdownMenuItem(text = { Text("Toggle Sidebar (Ctrl+B)") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; store.sidebarOpen = !store.sidebarOpen })
                        DropdownMenuItem(text = { Text("Two-Page Book Spread") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; store.twoPageSpread = !store.twoPageSpread })
                        DropdownMenuItem(text = { Text("Toggle Split View (Ctrl+\\)") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; store.splitViewEnabled = !store.splitViewEnabled })
                        DropdownMenuItem(text = { Text("Compare Documents…") }, enabled = store.activeTab?.kind == DocumentKind.PDF, onClick = { store.openMenuId = null; store.compareModalOpen = true })
                        DropdownMenuItem(text = { Text("Reading Mode (F11)") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.readingMode = !store.readingMode })
                        DropdownMenuItem(
                            text = { Text(if (store.autoScrollActive) "Stop Auto-Scroll" else "Start Auto-Scroll") },
                            enabled = store.activeTab?.kind == DocumentKind.PDF,
                            onClick = { store.openMenuId = null; store.autoScrollActive = !store.autoScrollActive },
                        )
                    }
                    MenuDropdownButton("Organize", store, palette) {
                        DropdownMenuItem(text = { Text("Page Organizer…") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.organizerOpen = true })
                        DropdownMenuItem(text = { Text("Combine PDFs…") }, onClick = { store.openMenuId = null; store.combineModalOpen = true })
                        DropdownMenuItem(text = { Text("Split / Extract Pages…") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.splitModalOpen = true })
                        DropdownMenuItem(text = { Text("Merge Another PDF…") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; actions.mergePdf() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Rotate Page 90° CW") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.rotateCurrentPage(1) })
                        DropdownMenuItem(text = { Text("Rotate Page 90° CCW") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.rotateCurrentPage(-1) })
                        DropdownMenuItem(text = { Text("Delete Current Page") }, enabled = (store.activeTab?.pages?.size ?: 0) > 1, onClick = { store.openMenuId = null; store.deleteCurrentPage() })
                    }
                    MenuDropdownButton("Help", store, palette) {
                        DropdownMenuItem(text = { Text("Command Palette (Ctrl+Shift+K)") }, onClick = { store.openMenuId = null; store.commandPaletteOpen = true })
                        DropdownMenuItem(text = { Text("Keyboard Shortcuts (Ctrl+/)") }, onClick = { store.openMenuId = null; store.shortcutsOpen = true })
                        DropdownMenuItem(text = { Text("Document Properties…") }, enabled = store.activeTab != null, onClick = { store.openMenuId = null; store.openDocumentProperties() })
                    }
                }

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    store.tabs.forEachIndexed { idx, tabItem ->
                        val active = idx == store.activeTabIndex
                        val idleBg = if (store.isDarkUi) Color(0x1408090C) else Color(0x0A000000)
                        val tabBorder = if (active) palette.accent.copy(alpha = 0.62f) else palette.border.copy(alpha = 0.35f)
                        val tabIcon = if (tabItem.kind == DocumentKind.DOCX) {
                            Icons.AutoMirrored.Rounded.Article
                        } else {
                            Icons.Rounded.Description
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) palette.activeFill else idleBg)
                                .border(0.75.dp, tabBorder, RoundedCornerShape(999.dp))
                                .clickable { store.selectTab(idx) }
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                tabIcon,
                                contentDescription = null,
                                tint = if (active) palette.accent else palette.textMuted,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = if (tabItem.dirty) "${tabItem.title} •" else tabItem.title,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) palette.text else palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 175.dp),
                            )
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close tab",
                                tint = if (active) palette.textMuted else palette.textMuted.copy(alpha = 0.72f),
                                modifier = Modifier.size(14.dp).clip(CircleShape).clickable { store.requestCloseTab(idx) },
                            )
                        }
                    }
                    SmallOverlayIconButton(onClick = actions::openPdf, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Add, contentDescription = "Open PDF", modifier = Modifier.size(15.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallOverlayIconButton(onClick = actions::minimize, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Minimize, contentDescription = "Minimize", modifier = Modifier.size(15.dp))
                    }
                    SmallOverlayIconButton(onClick = actions::toggleMaximize, tint = palette.textMuted) {
                        Icon(Icons.Rounded.CropSquare, contentDescription = "Maximize", modifier = Modifier.size(14.dp))
                    }
                    SmallOverlayIconButton(onClick = actions::close, tint = palette.danger) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDropdownButton(
    id: String,
    store: VnylStore,
    palette: GlassPalette,
    items: @Composable () -> Unit,
) {
    Box {
        GlassActionPill(
            onClick = { store.openMenuId = if (store.openMenuId == id) null else id },
            palette = palette,
            motionPreset = store.motionPreset,
            active = store.openMenuId == id,
            cornerRadius = 8.dp,
        ) {
            Text(id, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        DropdownMenu(
            expanded = store.openMenuId == id,
            onDismissRequest = { store.openMenuId = null },
            containerColor = if (store.isDarkUi) Color(0xFF16181E) else Color(0xFFFCFCFD),
            modifier = Modifier.border(0.75.dp, palette.border, RoundedCornerShape(10.dp)),
        ) {
            items()
        }
    }
}

@Composable
private fun FloatingToolbar(
    store: VnylStore,
    tab: PdfTabState?,
    palette: GlassPalette,
    actions: WindowActions,
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 1220.dp),
        cornerRadius = 20.dp,
        isDark = store.isDarkUi,
        style = store.uiStyle,
        glassLook = store.glassLook,
        refractionScale = store.refractionStrength,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tab != null && tab.kind == DocumentKind.PDF) {
                    GlassActionPill(onClick = { store.sidebarOpen = !store.sidebarOpen }, palette = palette, motionPreset = store.motionPreset, active = store.sidebarOpen) {
                        Icon(Icons.AutoMirrored.Rounded.ViewSidebar, contentDescription = "Sidebar", modifier = Modifier.size(16.dp))
                    }
                }
                GlassActionPill(onClick = actions::openPdf, palette = palette, motionPreset = store.motionPreset) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = "Open", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                if (tab != null) {
                    GlassActionPill(onClick = { actions.savePdf(tab.filePath == null) }, palette = palette, motionPreset = store.motionPreset, active = tab.dirty) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (tab.dirty) "Save*" else "Save", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    GlassActionPill(onClick = { store.undo() }, enabled = tab.canUndo, palette = palette, motionPreset = store.motionPreset) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo (Ctrl+Z)", modifier = Modifier.size(15.dp))
                    }
                    GlassActionPill(onClick = { store.redo() }, enabled = tab.canRedo, palette = palette, motionPreset = store.motionPreset) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo (Ctrl+Y)", modifier = Modifier.size(15.dp))
                    }
                    if (tab.kind == DocumentKind.PDF) {
                        GlassActionPill(onClick = { store.organizerOpen = true }, palette = palette, motionPreset = store.motionPreset) {
                            Icon(Icons.Rounded.GridView, contentDescription = "Page Organizer", modifier = Modifier.size(16.dp))
                        }
                        GlassActionPill(onClick = { store.rotateCurrentPage(1) }, palette = palette, motionPreset = store.motionPreset) {
                            Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate page", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (tab != null && tab.kind == DocumentKind.PDF) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ToolPill(ToolMode.SELECT, Icons.Rounded.TextFields, store, palette)
                    ToolPill(ToolMode.PAN, Icons.Rounded.PanTool, store, palette)
                    ToolPill(ToolMode.HIGHLIGHT, Icons.Rounded.BorderColor, store, palette)
                    ToolPill(ToolMode.INK, Icons.Rounded.Draw, store, palette)
                    ToolPill(ToolMode.NOTE, Icons.Rounded.EditNote, store, palette)
                    GlassActionPill(
                        onClick = { store.signatureModalOpen = true },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = store.activeTool == ToolMode.STAMP,
                    ) {
                        Icon(Icons.Rounded.HistoryEdu, contentDescription = "Sign", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sign", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }

                    if (store.activeTool in setOf(ToolMode.HIGHLIGHT, ToolMode.INK, ToolMode.NOTE)) {
                        Spacer(Modifier.width(4.dp))
                        VNYL_SWATCHES.forEachIndexed { idx, sw ->
                            val selected = idx == store.activeSwatchIndex
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 18.dp else 14.dp)
                                    .clip(CircleShape)
                                    .background(sw.color)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color.White else Color.Black.copy(alpha = 0.25f),
                                        shape = CircleShape,
                                    )
                                    .clickable { store.activeSwatchIndex = idx },
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tab != null && tab.kind == DocumentKind.PDF) {
                    GlassActionPill(onClick = { store.twoPageSpread = !store.twoPageSpread }, palette = palette, motionPreset = store.motionPreset, active = store.twoPageSpread) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Two-Page Spread", modifier = Modifier.size(16.dp))
                    }
                    GlassActionPill(onClick = { store.splitViewEnabled = !store.splitViewEnabled }, palette = palette, motionPreset = store.motionPreset, active = store.splitViewEnabled) {
                        Icon(Icons.Rounded.Splitscreen, contentDescription = "Split View", modifier = Modifier.size(16.dp))
                    }
                    GlassActionPill(onClick = { store.autoScrollActive = !store.autoScrollActive }, palette = palette, motionPreset = store.motionPreset, active = store.autoScrollActive) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Auto-scroll", modifier = Modifier.size(16.dp))
                    }
                    GlassActionPill(onClick = { store.readingMode = !store.readingMode }, palette = palette, motionPreset = store.motionPreset, active = store.readingMode) {
                        Icon(Icons.Rounded.AutoStories, contentDescription = "Reading Mode", modifier = Modifier.size(16.dp))
                    }
                    GlassActionPill(
                        onClick = {
                            store.searchOpen = !store.searchOpen
                            if (store.searchOpen) store.appearanceOpen = false
                        },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = store.searchOpen,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
                    }
                }
                GlassActionPill(
                    onClick = {
                        store.appearanceOpen = !store.appearanceOpen
                        if (store.appearanceOpen) store.searchOpen = false
                    },
                    palette = palette,
                    motionPreset = store.motionPreset,
                    active = store.appearanceOpen,
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Studio Appearance", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Studio", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ToolPill(
    tool: ToolMode,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    store: VnylStore,
    palette: GlassPalette,
) {
    GlassActionPill(
        onClick = { store.activeTool = tool },
        palette = palette,
        motionPreset = store.motionPreset,
        active = store.activeTool == tool,
    ) {
        Icon(icon, contentDescription = tool.label, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(tool.label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomPageAndZoomPill(
    store: VnylStore,
    tab: PdfTabState,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
) {
    var editingPage by remember { mutableStateOf(false) }
    var pageInput by remember(tab.currentPage) { mutableStateOf("${tab.currentPage + 1}") }
    var zoomMenuOpen by remember { mutableStateOf(false) }

    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 999.dp,
        isDark = store.isDarkUi,
        style = store.uiStyle,
        glassLook = store.glassLook,
        refractionScale = store.refractionStrength,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallOverlayIconButton(onClick = { store.jumpToPage(tab.currentPage - 1) }, enabled = tab.currentPage > 0, tint = palette.text) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page", modifier = Modifier.size(17.dp))
            }

            if (editingPage) {
                BasicTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .width(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.hover)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                                val target = (pageInput.toIntOrNull() ?: (tab.currentPage + 1)) - 1
                                store.jumpToPage(target)
                                editingPage = false
                                true
                            } else {
                                false
                            }
                        },
                )
                Text("/ ${tab.pages.size}", fontSize = 12.sp, color = palette.textMuted)
            } else {
                Text(
                    text = "Page ${tab.currentPage + 1} of ${tab.pages.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            pageInput = "${tab.currentPage + 1}"
                            editingPage = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            SmallOverlayIconButton(onClick = { store.jumpToPage(tab.currentPage + 1) }, enabled = tab.currentPage < tab.pages.lastIndex, tint = palette.text) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page", modifier = Modifier.size(17.dp))
            }

            Box(modifier = Modifier.width(1.dp).height(16.dp).background(palette.border))

            SmallOverlayIconButton(onClick = { store.updateZoom(tab.zoom * 0.88f, ZoomMode.CUSTOM) }, tint = palette.text) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out", modifier = Modifier.size(16.dp))
            }
            Box {
                Text(
                    text = when (tab.zoomMode) {
                        ZoomMode.FIT_WIDTH -> "Fit Width"
                        ZoomMode.FIT_PAGE -> "Fit Page"
                        ZoomMode.CUSTOM -> "${(tab.zoom * 100).roundToInt()}%"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { zoomMenuOpen = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                DropdownMenu(
                    expanded = zoomMenuOpen,
                    onDismissRequest = { zoomMenuOpen = false },
                    containerColor = if (store.isDarkUi) Color(0xFF16181E) else Color(0xFFFCFCFD),
                    modifier = Modifier.border(0.75.dp, palette.border, RoundedCornerShape(10.dp)),
                ) {
                    DropdownMenuItem(text = { Text("Fit Width") }, onClick = { zoomMenuOpen = false; store.updateZoom(tab.zoom, ZoomMode.FIT_WIDTH) })
                    DropdownMenuItem(text = { Text("Fit Page") }, onClick = { zoomMenuOpen = false; store.updateZoom(tab.zoom, ZoomMode.FIT_PAGE) })
                    HorizontalDivider()
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { z ->
                        DropdownMenuItem(
                            text = { Text("${(z * 100).roundToInt()}%") },
                            onClick = { zoomMenuOpen = false; store.updateZoom(z, ZoomMode.CUSTOM) },
                        )
                    }
                }
            }
            SmallOverlayIconButton(onClick = { store.updateZoom(tab.zoom * 1.14f, ZoomMode.CUSTOM) }, tint = palette.text) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in", modifier = Modifier.size(16.dp))
            }
            SmallOverlayIconButton(
                onClick = {
                    val next = if (tab.zoomMode == ZoomMode.FIT_WIDTH) ZoomMode.FIT_PAGE else ZoomMode.FIT_WIDTH
                    store.updateZoom(tab.zoom, next)
                },
                tint = if (tab.zoomMode != ZoomMode.CUSTOM) palette.accent else palette.text,
            ) {
                Icon(Icons.Rounded.FitScreen, contentDescription = "Toggle Fit Mode", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SidebarDrawer(
    store: VnylStore,
    tab: PdfTabState,
    palette: GlassPalette,
) {
    LiquidGlassSurface(
        modifier = Modifier.width(248.dp).fillMaxHeight(),
        cornerRadius = 20.dp,
        isDark = store.isDarkUi,
        style = store.uiStyle,
        glassLook = store.glassLook,
        refractionScale = store.refractionStrength,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            MorphingSegmentedControl(
                items = listOf(
                    SidebarTab.THUMBNAILS to "Pages",
                    SidebarTab.OUTLINE to "Outline",
                    SidebarTab.NOTES to "Notes",
                ),
                selected = store.sidebarTab,
                onSelect = {
                    store.sidebarTab = it
                    if (it == SidebarTab.NOTES) store.refreshAnnotations()
                },
                isDark = store.isDarkUi,
                motionPreset = store.motionPreset,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            when (store.sidebarTab) {
                SidebarTab.THUMBNAILS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        itemsIndexed(tab.pages) { idx, page ->
                            val active = idx == tab.currentPage
                            val aspect = (page.heightPts / page.widthPts.coerceAtLeast(1f)).coerceIn(0.5f, 2.0f)
                            val thumbW = 160
                            val thumbH = (thumbW * aspect).roundToInt().coerceIn(80, 320)
                            val img = store.thumbImages[tab.docId to idx]

                            LaunchedEffect(tab.docId, idx, store.pageRevisions[tab.docId to idx]) {
                                store.requestThumbRender(tab.docId, idx, thumbW, thumbH)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) palette.activeFill else Color.Transparent)
                                    .border(
                                        width = if (active) 1.5.dp else 0.dp,
                                        color = if (active) palette.accent else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { store.jumpToPage(idx) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height((120f * aspect).dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White),
                                ) {
                                    if (img != null) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawContext.canvas.nativeCanvas.drawImageRect(
                                                img,
                                                Rect.makeWH(img.width.toFloat(), img.height.toFloat()),
                                                Rect.makeWH(size.width, size.height),
                                                Paint().apply {
                                                    colorFilter = LiquidGlassShaders.pdfColorFilter(store.pdfColorMode)
                                                },
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) palette.accent else palette.textMuted,
                                )
                            }
                        }
                    }
                }
                SidebarTab.OUTLINE -> {
                    if (tab.outline.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No outline in this PDF", fontSize = 12.sp, color = palette.textMuted)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(tab.outline) { item ->
                                OutlineRow(item = item, depth = 0, palette = palette, onJump = { store.jumpToPage(it) })
                            }
                        }
                    }
                }
                SidebarTab.NOTES -> {
                    if (store.annotationsList.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No PDF annotations yet", fontSize = 12.sp, color = palette.textMuted)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(store.annotationsList) { annot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.hover)
                                        .clickable { store.jumpToPage(annot.pageIndex) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${annot.kind} • Page ${annot.pageIndex + 1}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                        if (annot.contents.isNotBlank()) {
                                            Text(annot.contents, fontSize = 11.sp, color = palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    SmallOverlayIconButton(onClick = { store.deleteAnnotationItem(annot) }, tint = palette.danger) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete annotation", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineRow(
    item: OutlineItemInfo,
    depth: Int,
    palette: GlassPalette,
    onJump: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = item.pageIndex != null) { item.pageIndex?.let(onJump) }
                .padding(start = (8 + depth * 12).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title.ifBlank { "Untitled" },
                fontSize = 12.sp,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.pageIndex?.let { Text("${it + 1}", fontSize = 11.sp, color = palette.textMuted) }
        }
        item.children.forEach { child ->
            OutlineRow(item = child, depth = depth + 1, palette = palette, onJump = onJump)
        }
    }
}

@Composable
private fun StartScreen(
    store: VnylStore,
    palette: GlassPalette,
    actions: WindowActions,
) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        LiquidGlassSurface(
            modifier = Modifier.width(560.dp),
            cornerRadius = 26.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Vnyl", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = palette.text)
                Text(
                    "Rust PDFium Core • Minimal Liquid Glass Studio",
                    fontSize = 12.5.sp,
                    color = palette.textMuted,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.hover)
                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Drop a .pdf or Word (.docx / .doc) file anywhere onto this window to open", fontSize = 12.5.sp, color = palette.textMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = actions::openPdf,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF / Word…", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { store.newBlankDocx() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                    ) {
                        Text("New Word (.docx)", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { store.openSampleDocument() },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                    ) {
                        Text("Guide", fontWeight = FontWeight.Medium)
                    }
                }
                if (store.recentFiles.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OverlaySectionLabel("Recent Documents", palette)
                        store.recentFiles.take(5).forEach { path ->
                            val f = File(path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { store.openFile(f) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(f.name, fontSize = 12.sp, color = palette.text, fontWeight = FontWeight.Medium)
                                Text(f.parent ?: "", fontSize = 11.sp, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientBackdrop(isDark: Boolean, isGlass: Boolean) {
    if (!isGlass) {
        Box(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF111214) else Color(0xFFF2F2F0)))
        return
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val shader = LiquidGlassShaders.createMinimalCanvasShader(size.width, size.height, isDark)
        val paint = Paint().apply { this.shader = shader }
        drawContext.canvas.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), paint)
    }
}
