package app.vnyl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.vnyl.native.DocxDocumentInfo
import app.vnyl.native.VnylNative
import app.vnyl.state.DocumentKind
import app.vnyl.state.PendingImageStamp
import app.vnyl.state.ToolMode
import app.vnyl.state.VnylStore
import app.vnyl.state.ZoomMode
import app.vnyl.ui.VnylApp
import app.vnyl.ui.WindowActions
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.awt.MouseInfo
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun main(args: Array<String>) {
    VnylNative.ensureLoaded()

    application {
        val store = remember { VnylStore() }
        val windowState = rememberWindowState(
            size = DpSize(1340.dp, 880.dp),
            placement = WindowPlacement.Floating,
        )

        LaunchedEffect(Unit) {
            val launchDoc = args.firstOrNull { path ->
                val f = File(path)
                f.isFile && (
                    f.extension.equals("pdf", ignoreCase = true) ||
                        f.extension.equals("docx", ignoreCase = true) ||
                        f.extension.equals("doc", ignoreCase = true)
                    )
            }
            if (launchDoc != null) {
                store.openFile(File(launchDoc))
            } else {
                store.openSampleDocument()
            }
        }

        fun pickOpenFile(title: String, extensions: Set<String> = setOf("pdf")): File? {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                setFilenameFilter { _, name ->
                    extensions.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
                }
                isVisible = true
            }
            val dir = dialog.directory ?: return null
            val file = dialog.file ?: return null
            return File(dir, file)
        }

        fun pickSaveFile(title: String, defaultName: String, ext: String): File? {
            val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
                file = defaultName
                isVisible = true
            }
            val dir = dialog.directory ?: return null
            val raw = dialog.file ?: return null
            val finalName = if (raw.endsWith(".$ext", ignoreCase = true)) raw else "$raw.$ext"
            return File(dir, finalName)
        }

        fun pickDirectory(title: String): File? = VnylNative.pickFolder(title)

        lateinit var windowRef: Frame

        val actions = remember {
            object : WindowActions {
                override fun openPdf() {
                    pickOpenFile("Open PDF or Word Document", setOf("pdf", "docx", "doc"))?.let { store.openFile(it) }
                }

                override fun pickPdfFile(title: String): File? = pickOpenFile(title)

                override fun pickP12File(): File? = pickOpenFile("Import Signing Certificate (.p12/.pfx)", setOf("p12", "pfx"))

                override fun pickSignedPdfDest(defaultName: String): File? =
                    pickSaveFile("Save Cryptographically Signed PDF", defaultName, "pdf")

                override fun savePdf(saveAs: Boolean) {
                    val tab = store.activeTab ?: return
                    val ext = if (tab.kind == DocumentKind.DOCX) "docx" else "pdf"
                    val existing = tab.filePath?.let { File(it) }
                    val target = if (!saveAs && existing != null) {
                        existing
                    } else {
                        val base = tab.title.removeSuffix(".pdf").removeSuffix(".docx")
                        pickSaveFile("Save ${ext.uppercase()}", "$base.$ext", ext)
                    }
                    target?.let { store.saveToPath(it) }
                }

                override fun mergePdf() {
                    pickOpenFile("Merge PDF into Current Document")?.let { store.mergePdfIntoActive(it) }
                }

                override fun exportPng() {
                    val tab = store.activeTab ?: return
                    val defaultName = "${tab.title.removeSuffix(".pdf")}-page-${tab.currentPage + 1}.png"
                    pickSaveFile("Export Page as PNG (150 DPI)", defaultName, "png")?.let {
                        store.exportPageAsImage(it, 150)
                    }
                }

                override fun exportText(markdown: Boolean) {
                    val tab = store.activeTab ?: return
                    val ext = if (markdown) "md" else "txt"
                    val defaultName = "${tab.title.removeSuffix(".pdf")}.$ext"
                    pickSaveFile("Export Document Text", defaultName, ext)?.let {
                        store.exportAsText(it, markdown)
                    }
                }

                override fun exportDocx() {
                    val tab = store.activeTab ?: return
                    val defaultName = "${tab.title.removeSuffix(".pdf")}.docx"
                    pickSaveFile("Export PDF to Word (.docx)", defaultName, "docx")?.let {
                        store.exportActivePdfToDocx(it)
                    }
                }

                override fun exportWordAsPdf(doc: DocxDocumentInfo) {
                    val defaultName = "${doc.title.removeSuffix(".docx")}.pdf"
                    pickSaveFile("Export Word as PDF", defaultName, "pdf")?.let {
                        store.exportActiveDocxToPdfFile(it)
                    }
                }

                override fun pickExtractFile(pagesOneBased: String) {
                    val tab = store.activeTab ?: return
                    val defaultName = "${tab.title.removeSuffix(".pdf")}-extracted.pdf"
                    pickSaveFile("Extract Pages ($pagesOneBased)", defaultName, "pdf")?.let {
                        store.extractPages(pagesOneBased, it)
                    }
                }

                override fun pickSplitFolder(chunkSize: Int) {
                    pickDirectory("Choose Output Folder for Split PDFs")?.let {
                        store.splitPdfByChunks(chunkSize, it)
                    }
                }

                override fun insertImage() {
                    val f = pickOpenFile("Insert Image (PNG/JPEG)", setOf("png", "jpg", "jpeg")) ?: return
                    runCatching { f.readBytes() }.onSuccess { bytes ->
                        store.pendingStamp = PendingImageStamp(f.name, bytes, 0.30f)
                        store.activeTool = ToolMode.STAMP
                        store.notifyStatus("Click anywhere on a PDF page to place ${f.name}")
                    }
                }

                override fun minimize() {
                    windowState.isMinimized = true
                }

                override fun toggleMaximize() {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                }

                override fun close() {
                    store.requestCloseApp { exitApplication() }
                }
            }
        }

        val windowIcon = remember { buildCrispWindowIconPainter() }

        Window(
            onCloseRequest = { store.requestCloseApp { exitApplication() } },
            state = windowState,
            undecorated = true,
            icon = windowIcon,
            title = store.activeTab?.let { "${it.title} — Vnyl" } ?: "Vnyl",
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.F11) {
                    if (store.activeTab != null) store.readingMode = !store.readingMode
                    true
                } else if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when (event.key) {
                        Key.O -> { actions.openPdf(); true }
                        Key.S -> { actions.savePdf(event.isShiftPressed); true }
                        Key.Z -> {
                            if (store.activeTab != null) {
                                if (event.isShiftPressed) store.redo() else store.undo()
                            }
                            true
                        }
                        Key.Y -> {
                            if (store.activeTab != null) store.redo()
                            true
                        }
                        Key.P -> { store.printDocument(); true }
                        Key.C -> { store.copySelectionToClipboard(); true }
                        Key.F -> {
                            if (store.activeTab != null) {
                                store.searchOpen = !store.searchOpen
                                if (store.searchOpen) store.appearanceOpen = false
                            }
                            true
                        }
                        Key.B -> {
                            if (store.activeTab != null) store.sidebarOpen = !store.sidebarOpen
                            true
                        }
                        Key.K -> {
                            if (event.isShiftPressed) {
                                store.commandPaletteOpen = !store.commandPaletteOpen
                                true
                            } else false
                        }
                        Key.Slash -> { store.shortcutsOpen = !store.shortcutsOpen; true }
                        Key.Backslash -> {
                            if (store.activeTab != null) store.splitViewEnabled = !store.splitViewEnabled
                            true
                        }
                        Key.W -> {
                            if (store.activeTabIndex >= 0) store.requestCloseTab(store.activeTabIndex)
                            true
                        }
                        Key.Equals -> {
                            store.activeTab?.let { store.updateZoom(it.zoom * 1.15f, ZoomMode.CUSTOM) }
                            true
                        }
                        Key.Minus -> {
                            store.activeTab?.let { store.updateZoom(it.zoom * 0.87f, ZoomMode.CUSTOM) }
                            true
                        }
                        Key.Zero -> {
                            store.activeTab?.let { store.updateZoom(1.0f, ZoomMode.FIT_WIDTH) }
                            true
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    when {
                        store.organizerOpen -> { store.organizerOpen = false; true }
                        store.pendingDirtyConfirm != null -> { store.pendingDirtyConfirm = null; true }
                        store.pendingPasswordFile != null -> {
                            store.pendingPasswordFile = null
                            store.passwordError = null
                            true
                        }
                        store.combineModalOpen -> { store.combineModalOpen = false; true }
                        store.keychainModalOpen -> { store.keychainModalOpen = false; true }
                        store.pendingFormEdit != null -> { store.pendingFormEdit = null; true }
                        store.splitModalOpen -> { store.splitModalOpen = false; true }
                        store.signatureModalOpen -> { store.signatureModalOpen = false; true }
                        store.compareModalOpen -> { store.compareModalOpen = false; true }
                        store.propertiesModalOpen -> { store.propertiesModalOpen = false; true }
                        store.commandPaletteOpen -> { store.commandPaletteOpen = false; true }
                        store.shortcutsOpen -> { store.shortcutsOpen = false; true }
                        store.pendingNote != null -> { store.pendingNote = null; true }
                        store.contextMenu != null -> { store.contextMenu = null; true }
                        store.searchOpen -> { store.searchOpen = false; store.clearSearch(); true }
                        store.appearanceOpen -> { store.appearanceOpen = false; true }
                        store.readingMode -> { store.readingMode = false; true }
                        else -> false
                    }
                } else {
                    false
                }
            },
        ) {
            windowRef = window

            val dropTarget = remember {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val data = event.dragData()
                        if (data is DragData.FilesList) {
                            data.readFiles().forEach { uriStr ->
                                runCatching { File(URI(uriStr)) }.getOrNull()?.let { f ->
                                    if (
                                        f.extension.equals("pdf", ignoreCase = true) ||
                                        f.extension.equals("docx", ignoreCase = true) ||
                                        f.extension.equals("doc", ignoreCase = true)
                                    ) {
                                        store.openFile(f)
                                    }
                                }
                            }
                            return true
                        }
                        return false
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
            ) {
                VnylApp(store = store, actions = actions)
                if (windowState.placement == WindowPlacement.Floating) {
                    WindowResizeGrips(windowRef)
                }
            }
        }
    }
}

@Composable
private fun WindowResizeGrips(window: Frame) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(6.dp)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(window) {
                    detectDragGestures { _, _ ->
                        val p = MouseInfo.getPointerInfo().location
                        window.setSize((p.x - window.x).coerceAtLeast(720), window.height)
                    }
                },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(6.dp)
                .fillMaxWidth()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR)))
                .pointerInput(window) {
                    detectDragGestures { _, _ ->
                        val p = MouseInfo.getPointerInfo().location
                        window.setSize(window.width, (p.y - window.y).coerceAtLeast(520))
                    }
                },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
                .pointerInput(window) {
                    detectDragGestures { _, _ ->
                        val p = MouseInfo.getPointerInfo().location
                        window.setSize(
                            (p.x - window.x).coerceAtLeast(720),
                            (p.y - window.y).coerceAtLeast(520),
                        )
                    }
                },
        )
    }
}

private fun buildCrispWindowIconPainter(): BitmapPainter {
    val size = 64f
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(size.toInt(), size.toInt())
    val canvas = surface.canvas
    val bgPaint = org.jetbrains.skia.Paint().apply {
        isAntiAlias = true
        color = 0xFF14161B.toInt()
    }
    canvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(2f, 2f, 60f, 60f, 16f), bgPaint)
    val rimPaint = org.jetbrains.skia.Paint().apply {
        isAntiAlias = true
        mode = org.jetbrains.skia.PaintMode.STROKE
        strokeWidth = 2f
        color = 0xFF5A96FF.toInt()
    }
    canvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(2f, 2f, 60f, 60f, 16f), rimPaint)
    val vPaint = org.jetbrains.skia.Paint().apply {
        isAntiAlias = true
        mode = org.jetbrains.skia.PaintMode.STROKE
        strokeWidth = 6.5f
        strokeCap = org.jetbrains.skia.PaintStrokeCap.ROUND
        strokeJoin = org.jetbrains.skia.PaintStrokeJoin.ROUND
        color = 0xFFF5F6FA.toInt()
    }
    val path = org.jetbrains.skia.Path().apply {
        moveTo(18f, 20f)
        lineTo(32f, 46f)
        lineTo(46f, 20f)
    }
    canvas.drawPath(path, vPaint)
    return BitmapPainter(surface.makeImageSnapshot().toComposeImageBitmap())
}
