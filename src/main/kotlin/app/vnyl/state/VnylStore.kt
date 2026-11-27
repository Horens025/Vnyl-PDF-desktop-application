package app.vnyl.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import app.vnyl.native.AnnotationItemInfo
import app.vnyl.native.DocumentMetadataInfo
import app.vnyl.native.DocumentOpenInfo
import app.vnyl.native.DocxBlock
import app.vnyl.native.DocxDocumentInfo
import app.vnyl.native.DocxRun
import app.vnyl.native.FormFieldInfo
import app.vnyl.native.OfficeConverterInfo
import app.vnyl.native.OutlineItemInfo
import app.vnyl.native.PageLinkInfo
import app.vnyl.native.PageSizeInfo
import app.vnyl.native.SearchHitInfo
import app.vnyl.native.TextSpanInfo
import app.vnyl.native.VnylNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import java.awt.Graphics2D
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File

enum class UiTheme { LIGHT, DARK, SYSTEM }
enum class UiStyle { LIQUID_GLASS, CLASSIC }
enum class GlassLook { FROSTED, CLEAR }
enum class PdfColorMode { LIGHT, DARK, SEPIA }
enum class MotionPreset { BOUNCY, SNAPPY, REDUCED }
enum class SidebarTab { THUMBNAILS, OUTLINE, NOTES }
enum class ZoomMode { CUSTOM, FIT_WIDTH, FIT_PAGE }
enum class DocumentKind { PDF, DOCX }
enum class ToolMode(val label: String) {
    SELECT("Select"),
    PAN("Hand"),
    HIGHLIGHT("Highlight"),
    INK("Draw"),
    NOTE("Note"),
    STAMP("Stamp"),
}

data class AnnotSwatch(val name: String, val argb: Int, val color: Color)

val VNYL_SWATCHES = listOf(
    AnnotSwatch("Sun", 0x99FFEB3B.toInt(), Color(0xFFFFD60A)),
    AnnotSwatch("Mint", 0x9934D399.toInt(), Color(0xFF30D158)),
    AnnotSwatch("Sky", 0x9938BDF8.toInt(), Color(0xFF0A84FF)),
    AnnotSwatch("Rose", 0x99FB7185.toInt(), Color(0xFFFF375F)),
    AnnotSwatch("Amber", 0x99FB923C.toInt(), Color(0xFFFF9F0A)),
    AnnotSwatch("Iris", 0x99A78BFA.toInt(), Color(0xFFBF5AF2)),
)

data class PdfTabState(
    val docId: Long,
    val title: String,
    val filePath: String?,
    val pages: List<PageSizeInfo>,
    val outline: List<OutlineItemInfo> = emptyList(),
    var currentPage: Int = 0,
    var zoom: Float = 1.15f,
    var zoomMode: ZoomMode = ZoomMode.FIT_WIDTH,
    var dirty: Boolean = false,
    val kind: DocumentKind = DocumentKind.PDF,
    val docxDoc: DocxDocumentInfo? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val docxUndoStack: List<DocxDocumentInfo> = emptyList(),
    val docxRedoStack: List<DocxDocumentInfo> = emptyList(),
)

sealed interface DirtyConfirmAction {
    data class CloseTab(val index: Int) : DirtyConfirmAction
    data class ExitApp(val onExit: () -> Unit) : DirtyConfirmAction
}

data class PageRenderKey(
    val docId: Long,
    val pageIndex: Int,
    val widthPx: Int,
    val revision: Int,
)

data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class TextSelectionState(
    val docId: Long,
    val pageIndex: Int,
    val rects: List<NormRect>,
    val text: String,
)

data class PendingNoteRequest(
    val docId: Long,
    val pageIndex: Int,
    val normX: Float,
    val normY: Float,
)

data class ContextMenuState(
    val offset: DpOffset,
    val pageIndex: Int,
    val normPos: Offset,
)

data class PendingImageStamp(
    val label: String,
    val pngBytes: ByteArray,
    val normWidth: Float = 0.28f,
)

data class PendingFormEdit(
    val docId: Long,
    val field: FormFieldInfo,
)

@Serializable
data class SigningIdentityMeta(
    val id: String,
    val label: String,
    val fileName: String,
)

@Serializable
data class PersistedPrefs(
    val uiTheme: UiTheme = UiTheme.DARK,
    val uiStyle: UiStyle = UiStyle.LIQUID_GLASS,
    val glassLook: GlassLook = GlassLook.FROSTED,
    val pdfColorMode: PdfColorMode = PdfColorMode.LIGHT,
    val motionPreset: MotionPreset = MotionPreset.BOUNCY,
    val refractionStrength: Float = 1.0f,
    val recentFiles: List<String> = emptyList(),
    val signingIdentities: List<SigningIdentityMeta> = emptyList(),
)

class VnylStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var nextLocalDocId = -1L

    val tabs = mutableStateListOf<PdfTabState>()
    var activeTabIndex by mutableStateOf(-1)
        private set

    val activeTab: PdfTabState?
        get() = tabs.getOrNull(activeTabIndex)

    // Appearance & Liquid Glass settings
    var uiTheme by mutableStateOf(UiTheme.DARK)
    var uiStyle by mutableStateOf(UiStyle.LIQUID_GLASS)
    var glassLook by mutableStateOf(GlassLook.FROSTED)
    var pdfColorMode by mutableStateOf(PdfColorMode.LIGHT)
    var motionPreset by mutableStateOf(MotionPreset.BOUNCY)
    var refractionStrength by mutableStateOf(1.0f)

    // Tools, Annotations & Signatures
    var activeTool by mutableStateOf(ToolMode.SELECT)
    var activeSwatchIndex by mutableStateOf(0)
    val activeSwatch: AnnotSwatch get() = VNYL_SWATCHES[activeSwatchIndex.coerceIn(VNYL_SWATCHES.indices)]
    var pendingNote by mutableStateOf<PendingNoteRequest?>(null)
    var pendingStamp by mutableStateOf<PendingImageStamp?>(null)
    var pendingFormEdit by mutableStateOf<PendingFormEdit?>(null)
    val savedSignatures = mutableStateListOf<PendingImageStamp>()
    val signingIdentities = mutableStateListOf<SigningIdentityMeta>()
    val annotationsList = mutableStateListOf<AnnotationItemInfo>()

    // Panels & Overlays
    var sidebarOpen by mutableStateOf(true)
    var sidebarTab by mutableStateOf(SidebarTab.THUMBNAILS)
    var searchOpen by mutableStateOf(false)
    var appearanceOpen by mutableStateOf(false)
    var readingMode by mutableStateOf(false)
    var splitViewEnabled by mutableStateOf(false)
    var twoPageSpread by mutableStateOf(false)
    var commandPaletteOpen by mutableStateOf(false)
    var shortcutsOpen by mutableStateOf(false)
    var organizerOpen by mutableStateOf(false)
    var splitModalOpen by mutableStateOf(false)
    var combineModalOpen by mutableStateOf(false)
    var signatureModalOpen by mutableStateOf(false)
    var keychainModalOpen by mutableStateOf(false)
    var compareModalOpen by mutableStateOf(false)
    var propertiesModalOpen by mutableStateOf(false)
    var activeMetadata by mutableStateOf<DocumentMetadataInfo?>(null)
    var pendingPasswordFile by mutableStateOf<File?>(null)
    var passwordError by mutableStateOf<String?>(null)
    var pendingDirtyConfirm by mutableStateOf<DirtyConfirmAction?>(null)
    var selectedAnnotation by mutableStateOf<AnnotationItemInfo?>(null)
    var officeConverter by mutableStateOf(OfficeConverterInfo())
    var openMenuId by mutableStateOf<String?>(null)
    var contextMenu by mutableStateOf<ContextMenuState?>(null)

    // Auto-scroll
    var autoScrollActive by mutableStateOf(false)
    var autoScrollSpeed by mutableStateOf(48f)

    // Search
    var searchQuery by mutableStateOf("")
    val searchHits = mutableStateListOf<SearchHitInfo>()
    var activeHitIndex by mutableStateOf(-1)
    var isSearching by mutableStateOf(false)

    // Text selection, links & forms
    var selection by mutableStateOf<TextSelectionState?>(null)
    val textSpansCache = mutableStateMapOf<Pair<Long, Int>, List<TextSpanInfo>>()
    val pageLinksCache = mutableStateMapOf<Pair<Long, Int>, List<PageLinkInfo>>()
    val formFieldsCache = mutableStateMapOf<Pair<Long, Int>, List<FormFieldInfo>>()
    private val inflightSpans = mutableSetOf<Pair<Long, Int>>()
    private val inflightLinks = mutableSetOf<Pair<Long, Int>>()
    private val inflightForms = mutableSetOf<Pair<Long, Int>>()

    // Status toast
    var statusMessage by mutableStateOf<String?>(null)
    var statusIsError by mutableStateOf(false)

    // Recents & caches
    val recentFiles = mutableStateListOf<String>()
    val pageRevisions = mutableStateMapOf<Pair<Long, Int>, Int>()
    val pageImages = mutableStateMapOf<PageRenderKey, Image>()
    val thumbImages = mutableStateMapOf<Pair<Long, Int>, Image>()
    private val inflightPages = mutableSetOf<PageRenderKey>()
    private val inflightThumbs = mutableSetOf<Pair<Long, Int>>()

    var scrollToPageTrigger by mutableStateOf<Pair<Int, Long>?>(null)

    init {
        loadPrefs()
        scope.launch {
            val info = runCatching { VnylNative.detectOfficeConverter() }.getOrDefault(OfficeConverterInfo())
            withContext(Dispatchers.Main) { officeConverter = info }
        }
    }

    val isDarkUi: Boolean
        get() = when (uiTheme) {
            UiTheme.DARK -> true
            UiTheme.LIGHT -> false
            UiTheme.SYSTEM -> true
        }

    private fun prefsFile(): File {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        return File(File(base, "Vnyl"), "prefs.json")
    }

    fun persistPrefs() {
        val snap = PersistedPrefs(
            uiTheme = uiTheme,
            uiStyle = uiStyle,
            glassLook = glassLook,
            pdfColorMode = pdfColorMode,
            motionPreset = motionPreset,
            refractionStrength = refractionStrength,
            recentFiles = recentFiles.toList(),
            signingIdentities = signingIdentities.toList(),
        )
        scope.launch {
            runCatching {
                val f = prefsFile()
                f.parentFile?.mkdirs()
                f.writeText(json.encodeToString(snap))
            }
        }
    }

    private fun loadPrefs() {
        runCatching {
            val f = prefsFile()
            if (!f.exists()) return
            val p = json.decodeFromString<PersistedPrefs>(f.readText())
            uiTheme = p.uiTheme
            uiStyle = p.uiStyle
            glassLook = p.glassLook
            pdfColorMode = p.pdfColorMode
            motionPreset = p.motionPreset
            refractionStrength = p.refractionStrength
            recentFiles.clear()
            recentFiles.addAll(p.recentFiles.filter { File(it).isFile }.take(10))
            signingIdentities.clear()
            signingIdentities.addAll(p.signingIdentities)
        }
    }

    fun notifyStatus(msg: String, isError: Boolean = false) {
        statusMessage = msg
        statusIsError = isError
    }

    fun openFile(file: File, password: String? = null) {
        if (!file.exists() || !file.isFile) {
            notifyStatus("File not found: ${file.name}", isError = true)
            return
        }
        val existingIdx = tabs.indexOfFirst { it.filePath == file.absolutePath }
        if (existingIdx >= 0) {
            activeTabIndex = existingIdx
            refreshAnnotations()
            return
        }

        val ext = file.extension.lowercase()
        if (ext == "docx") {
            scope.launch {
                try {
                    val docx = VnylNative.parseDocx(file.absolutePath)
                    withContext(Dispatchers.Main) {
                        val id = nextLocalDocId--
                        tabs.add(
                            PdfTabState(
                                docId = id,
                                title = file.name,
                                filePath = file.absolutePath,
                                pages = listOf(PageSizeInfo(612f, 792f)),
                                kind = DocumentKind.DOCX,
                                docxDoc = docx,
                            )
                        )
                        activeTabIndex = tabs.lastIndex
                        recentFiles.remove(file.absolutePath)
                        recentFiles.add(0, file.absolutePath)
                        if (recentFiles.size > 10) recentFiles.removeAt(recentFiles.lastIndex)
                        persistPrefs()
                        notifyStatus("Opened Word document ${file.name} (${docx.blocks.size} blocks)")
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        notifyStatus("Could not open ${file.name}: ${t.message}", isError = true)
                    }
                }
            }
            return
        }

        if (ext == "doc") {
            openWordViaOfficeLayout(file)
            return
        }

        scope.launch {
            try {
                val info: DocumentOpenInfo = VnylNative.openDocument(file.absolutePath, password)
                val outline = runCatching { VnylNative.getOutline(info.docId) }.getOrDefault(emptyList())
                withContext(Dispatchers.Main) {
                    pendingPasswordFile = null
                    passwordError = null
                    tabs.add(
                        PdfTabState(
                            docId = info.docId,
                            title = info.title,
                            filePath = file.absolutePath,
                            pages = info.pages,
                            outline = outline,
                        )
                    )
                    activeTabIndex = tabs.lastIndex
                    recentFiles.remove(file.absolutePath)
                    recentFiles.add(0, file.absolutePath)
                    if (recentFiles.size > 10) recentFiles.removeAt(recentFiles.lastIndex)
                    persistPrefs()
                    refreshAnnotations()
                    notifyStatus("Opened ${file.name} (${info.pageCount} pages)")
                }
            } catch (t: Throwable) {
                val msg = t.message.orEmpty()
                withContext(Dispatchers.Main) {
                    if (msg.contains("PASSWORD_REQUIRED") || msg.contains("PASSWORD_INCORRECT")) {
                        pendingPasswordFile = file
                        passwordError = if (msg.contains("PASSWORD_INCORRECT")) {
                            "Incorrect password. Please try again."
                        } else {
                            null
                        }
                    } else {
                        notifyStatus("Could not open ${file.name}: $msg", isError = true)
                    }
                }
            }
        }
    }

    fun newBlankDocx() {
        val id = nextLocalDocId--
        val blank = DocxDocumentInfo(
            title = "Untitled.docx",
            blocks = listOf(
                DocxBlock(
                    id = "b1",
                    kind = "h1",
                    runs = listOf(DocxRun("Untitled Document", bold = true)),
                ),
                DocxBlock(
                    id = "b2",
                    kind = "paragraph",
                    runs = listOf(DocxRun("")),
                ),
            ),
        )
        tabs.add(
            PdfTabState(
                docId = id,
                title = "Untitled.docx",
                filePath = null,
                pages = listOf(PageSizeInfo(612f, 792f)),
                dirty = true,
                kind = DocumentKind.DOCX,
                docxDoc = blank,
            )
        )
        activeTabIndex = tabs.lastIndex
        notifyStatus("Created blank Word (.docx) document")
    }

    fun updateDocxDocument(updated: DocxDocumentInfo) {
        val tab = activeTab ?: return
        if (tab.kind != DocumentKind.DOCX) return
        val prev = tab.docxDoc
        val nextUndo = if (prev != null) (tab.docxUndoStack + prev).takeLast(24) else tab.docxUndoStack
        tabs[activeTabIndex] = tab.copy(
            docxDoc = updated,
            dirty = true,
            docxUndoStack = nextUndo,
            docxRedoStack = emptyList(),
            canUndo = nextUndo.isNotEmpty(),
            canRedo = false,
        )
    }

    fun convertActiveDocxToPdfTab() {
        val tab = activeTab ?: return
        val docx = tab.docxDoc ?: return
        scope.launch {
            try {
                val info = VnylNative.convertDocxToPdfTab(docx)
                withContext(Dispatchers.Main) {
                    tabs.add(
                        PdfTabState(
                            docId = info.docId,
                            title = info.title,
                            filePath = null,
                            pages = info.pages,
                            dirty = true,
                        )
                    )
                    activeTabIndex = tabs.lastIndex
                    refreshAnnotations()
                    notifyStatus("Converted ${tab.title} to PDF (${info.pageCount} pages)")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("DOCX->PDF failed: ${t.message}", isError = true) }
            }
        }
    }

    fun exportActiveDocxToPdfFile(destFile: File) {
        val tab = activeTab ?: return
        val docx = tab.docxDoc ?: return
        scope.launch {
            try {
                VnylNative.exportDocxToPdfFile(docx, destFile.absolutePath)
                withContext(Dispatchers.Main) { notifyStatus("Exported PDF to ${destFile.name}") }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Export PDF failed: ${t.message}", isError = true) }
            }
        }
    }

    fun openWordViaOfficeLayout(srcFile: File) {
        scope.launch {
            try {
                val info = VnylNative.convertWordViaOfficeToPdfTab(srcFile.absolutePath)
                withContext(Dispatchers.Main) {
                    tabs.add(
                        PdfTabState(
                            docId = info.docId,
                            title = info.title,
                            filePath = null,
                            pages = info.pages,
                        )
                    )
                    activeTabIndex = tabs.lastIndex
                    refreshAnnotations()
                    notifyStatus("Opened ${srcFile.name} layout via ${officeConverter.engine}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("${t.message}", isError = true) }
            }
        }
    }

    fun exportActivePdfToDocx(destFile: File) {
        val tab = activeTab ?: return
        if (tab.kind != DocumentKind.PDF) return
        scope.launch {
            try {
                VnylNative.exportPdfToDocx(tab.docId, tab.pages.size, tab.title, destFile.absolutePath)
                withContext(Dispatchers.Main) { notifyStatus("Exported Word document ${destFile.name}") }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("PDF->DOCX failed: ${t.message}", isError = true) }
            }
        }
    }

    fun signActivePdfCryptographic(identityId: String, password: String, reason: String, destFile: File) {
        val tab = activeTab ?: return
        if (tab.kind != DocumentKind.PDF) return
        scope.launch {
            try {
                VnylNative.signPdfPkcs12ToFile(tab.docId, identityId, password, reason, destFile.absolutePath)
                withContext(Dispatchers.Main) {
                    notifyStatus("Cryptographically signed PDF saved to ${destFile.name}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("PKCS#7 signing failed: ${t.message}", isError = true) }
            }
        }
    }

    fun openSampleDocument() {
        scope.launch {
            try {
                val bytes = buildSamplePdf()
                val info = VnylNative.openDocumentBytes(bytes, "Vnyl Liquid Glass Guide.pdf")
                withContext(Dispatchers.Main) {
                    tabs.add(
                        PdfTabState(
                            docId = info.docId,
                            title = "Vnyl Guide.pdf",
                            filePath = null,
                            pages = info.pages,
                            outline = listOf(
                                OutlineItemInfo("Single-Canvas Skiko Pipeline", 0),
                                OutlineItemInfo("Tools, Organizer & Signing", 1),
                            ),
                        )
                    )
                    activeTabIndex = tabs.lastIndex
                    refreshAnnotations()
                    statusMessage = null
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notifyStatus("Sample PDF failed: ${t.message}", isError = true)
                }
            }
        }
    }

    fun selectTab(index: Int) {
        if (index in tabs.indices) {
            activeTabIndex = index
            selection = null
            clearSearch()
            refreshAnnotations()
        }
    }

    fun requestCloseTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        if (tab.dirty) {
            pendingDirtyConfirm = DirtyConfirmAction.CloseTab(index)
        } else {
            closeTab(index)
        }
    }

    fun requestCloseApp(onExit: () -> Unit) {
        if (tabs.any { it.dirty }) {
            pendingDirtyConfirm = DirtyConfirmAction.ExitApp(onExit)
        } else {
            onExit()
        }
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        tabs.removeAt(index)
        if (tab.kind == DocumentKind.PDF && tab.docId > 0) {
            pageImages.keys.filter { it.docId == tab.docId }.toList().forEach { pageImages.remove(it) }
            thumbImages.keys.filter { it.first == tab.docId }.toList().forEach { thumbImages.remove(it) }
            textSpansCache.keys.filter { it.first == tab.docId }.toList().forEach { textSpansCache.remove(it) }
            pageLinksCache.keys.filter { it.first == tab.docId }.toList().forEach { pageLinksCache.remove(it) }
            formFieldsCache.keys.filter { it.first == tab.docId }.toList().forEach { formFieldsCache.remove(it) }
            scope.launch { VnylNative.closeDocument(tab.docId) }
        }
        selection = null
        selectedAnnotation = null
        activeTabIndex = when {
            tabs.isEmpty() -> -1
            index <= activeTabIndex -> (activeTabIndex - 1).coerceAtLeast(0)
            else -> activeTabIndex
        }
        refreshAnnotations()
    }

    fun requestPageRender(docId: Long, pageIndex: Int, widthPx: Int, heightPx: Int) {
        val rev = pageRevisions[docId to pageIndex] ?: 0
        val key = PageRenderKey(docId, pageIndex, widthPx, rev)
        if (pageImages.containsKey(key) || !synchronized(inflightPages) { inflightPages.add(key) }) {
            return
        }
        scope.launch {
            try {
                val img = VnylNative.renderPageToSkiaImage(docId, pageIndex, widthPx, heightPx)
                withContext(Dispatchers.Main) {
                    if (pageImages.size > 36) {
                        val stale = pageImages.keys.firstOrNull {
                            it.docId != activeTab?.docId ||
                                kotlin.math.abs(it.pageIndex - (activeTab?.currentPage ?: 0)) > 6
                        } ?: pageImages.keys.firstOrNull()
                        if (stale != null) pageImages.remove(stale)
                    }
                    pageImages[key] = img
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(inflightPages) { inflightPages.remove(key) }
            }
        }
    }

    fun requestThumbRender(docId: Long, pageIndex: Int, widthPx: Int, heightPx: Int) {
        val key = docId to pageIndex
        if (thumbImages.containsKey(key) || !synchronized(inflightThumbs) { inflightThumbs.add(key) }) {
            return
        }
        scope.launch {
            try {
                val img = VnylNative.renderPageToSkiaImage(docId, pageIndex, widthPx, heightPx)
                withContext(Dispatchers.Main) {
                    thumbImages[key] = img
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(inflightThumbs) { inflightThumbs.remove(key) }
            }
        }
    }

    fun ensureTextSpans(docId: Long, pageIndex: Int) {
        val key = docId to pageIndex
        if (textSpansCache.containsKey(key) || !synchronized(inflightSpans) { inflightSpans.add(key) }) {
            return
        }
        scope.launch {
            try {
                val spans = VnylNative.getPageTextSpans(docId, pageIndex)
                withContext(Dispatchers.Main) {
                    textSpansCache[key] = spans
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(inflightSpans) { inflightSpans.remove(key) }
            }
        }
    }

    fun ensurePageLinks(docId: Long, pageIndex: Int) {
        val key = docId to pageIndex
        if (pageLinksCache.containsKey(key) || !synchronized(inflightLinks) { inflightLinks.add(key) }) {
            return
        }
        scope.launch {
            try {
                val links = VnylNative.getPageLinks(docId, pageIndex)
                withContext(Dispatchers.Main) {
                    pageLinksCache[key] = links
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(inflightLinks) { inflightLinks.remove(key) }
            }
        }
    }

    fun ensureFormFields(docId: Long, pageIndex: Int) {
        val key = docId to pageIndex
        if (formFieldsCache.containsKey(key) || !synchronized(inflightForms) { inflightForms.add(key) }) {
            return
        }
        scope.launch {
            try {
                val fields = VnylNative.getFormFields(docId, pageIndex)
                withContext(Dispatchers.Main) {
                    formFieldsCache[key] = fields
                }
            } catch (_: Throwable) {
            } finally {
                synchronized(inflightForms) { inflightForms.remove(key) }
            }
        }
    }

    fun refreshAnnotations() {
        val tab = activeTab
        if (tab == null) {
            annotationsList.clear()
            return
        }
        scope.launch {
            val items = runCatching { VnylNative.getAnnotations(tab.docId) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                annotationsList.clear()
                annotationsList.addAll(items)
            }
        }
    }

    fun openDocumentProperties() {
        val tab = activeTab ?: return
        scope.launch {
            val meta = runCatching { VnylNative.getMetadata(tab.docId) }.getOrNull()
            withContext(Dispatchers.Main) {
                activeMetadata = meta
                propertiesModalOpen = true
            }
        }
    }

    private fun invalidatePageRender(docId: Long, pageIndex: Int? = null) {
        if (pageIndex == null) {
            pageImages.keys.filter { it.docId == docId }.toList().forEach { pageImages.remove(it) }
            thumbImages.keys.filter { it.first == docId }.toList().forEach { thumbImages.remove(it) }
            textSpansCache.keys.filter { it.first == docId }.toList().forEach { textSpansCache.remove(it) }
            pageLinksCache.keys.filter { it.first == docId }.toList().forEach { pageLinksCache.remove(it) }
            formFieldsCache.keys.filter { it.first == docId }.toList().forEach { formFieldsCache.remove(it) }
            pageRevisions.keys.filter { it.first == docId }.toList().forEach { k ->
                pageRevisions[k] = (pageRevisions[k] ?: 0) + 1
            }
        } else {
            pageRevisions[docId to pageIndex] = (pageRevisions[docId to pageIndex] ?: 0) + 1
            pageImages.keys.filter { it.docId == docId && it.pageIndex == pageIndex }.toList().forEach { pageImages.remove(it) }
            thumbImages.remove(docId to pageIndex)
            textSpansCache.remove(docId to pageIndex)
            pageLinksCache.remove(docId to pageIndex)
            formFieldsCache.remove(docId to pageIndex)
        }
    }

    fun refreshUndoState(docId: Long) {
        scope.launch {
            val pair = runCatching { VnylNative.getUndoState(docId) }.getOrDefault(false to false)
            withContext(Dispatchers.Main) {
                val idx = tabs.indexOfFirst { it.docId == docId }
                if (idx >= 0) {
                    tabs[idx] = tabs[idx].copy(canUndo = pair.first, canRedo = pair.second)
                }
            }
        }
    }

    fun undo() {
        val tab = activeTab ?: return
        if (tab.kind == DocumentKind.DOCX) {
            val prev = tab.docxUndoStack.lastOrNull() ?: return
            val cur = tab.docxDoc ?: return
            val nextUndo = tab.docxUndoStack.dropLast(1)
            val nextRedo = (tab.docxRedoStack + cur).takeLast(24)
            tabs[activeTabIndex] = tab.copy(
                docxDoc = prev,
                dirty = true,
                docxUndoStack = nextUndo,
                docxRedoStack = nextRedo,
                canUndo = nextUndo.isNotEmpty(),
                canRedo = nextRedo.isNotEmpty(),
            )
            notifyStatus("Undid Word edit")
            return
        }
        scope.launch {
            try {
                val updated = VnylNative.undoDocument(tab.docId)
                val pair = runCatching { VnylNative.getUndoState(tab.docId) }.getOrDefault(false to true)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) {
                        tabs[idx] = tabs[idx].copy(
                            pages = updated.pages,
                            currentPage = tabs[idx].currentPage.coerceAtMost((updated.pageCount - 1).coerceAtLeast(0)),
                            dirty = true,
                            canUndo = pair.first,
                            canRedo = pair.second,
                        )
                    }
                    invalidatePageRender(tab.docId, null)
                    refreshAnnotations()
                    notifyStatus("Undid last change")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus(t.message ?: "Nothing to undo") }
            }
        }
    }

    fun redo() {
        val tab = activeTab ?: return
        if (tab.kind == DocumentKind.DOCX) {
            val next = tab.docxRedoStack.lastOrNull() ?: return
            val cur = tab.docxDoc ?: return
            val nextRedo = tab.docxRedoStack.dropLast(1)
            val nextUndo = (tab.docxUndoStack + cur).takeLast(24)
            tabs[activeTabIndex] = tab.copy(
                docxDoc = next,
                dirty = true,
                docxUndoStack = nextUndo,
                docxRedoStack = nextRedo,
                canUndo = nextUndo.isNotEmpty(),
                canRedo = nextRedo.isNotEmpty(),
            )
            notifyStatus("Redid Word edit")
            return
        }
        scope.launch {
            try {
                val updated = VnylNative.redoDocument(tab.docId)
                val pair = runCatching { VnylNative.getUndoState(tab.docId) }.getOrDefault(true to false)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) {
                        tabs[idx] = tabs[idx].copy(
                            pages = updated.pages,
                            currentPage = tabs[idx].currentPage.coerceAtMost((updated.pageCount - 1).coerceAtLeast(0)),
                            dirty = true,
                            canUndo = pair.first,
                            canRedo = pair.second,
                        )
                    }
                    invalidatePageRender(tab.docId, null)
                    refreshAnnotations()
                    notifyStatus("Redid change")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus(t.message ?: "Nothing to redo") }
            }
        }
    }

    fun combineFiles(files: List<File>, title: String = "Combined.pdf") {
        if (files.isEmpty()) return
        scope.launch {
            try {
                val info = VnylNative.combinePdfFiles(files.map { it.absolutePath }, title)
                withContext(Dispatchers.Main) {
                    tabs.add(
                        PdfTabState(
                            docId = info.docId,
                            title = info.title,
                            filePath = null,
                            pages = info.pages,
                            dirty = true,
                        )
                    )
                    activeTabIndex = tabs.lastIndex
                    notifyStatus("Combined ${files.size} PDFs (${info.pageCount} pages)")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Combine failed: ${t.message}", isError = true) }
            }
        }
    }

    fun updateFormField(docId: Long, field: FormFieldInfo, value: String, checked: Boolean) {
        scope.launch {
            try {
                VnylNative.setFormField(docId, field.pageIndex, field.annotIndex, value, checked)
                withContext(Dispatchers.Main) {
                    invalidatePageRender(docId, field.pageIndex)
                    markDirty()
                    notifyStatus("Updated field ${field.name.ifBlank { field.kind }}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Form update failed: ${t.message}", isError = true) }
            }
        }
    }

    fun prepareSymbolStamp(isCheck: Boolean) {
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(96, 96)
        val canvas = surface.canvas
        val paint = org.jetbrains.skia.Paint().apply {
            isAntiAlias = true
            mode = org.jetbrains.skia.PaintMode.STROKE
            strokeWidth = 10f
            strokeCap = org.jetbrains.skia.PaintStrokeCap.ROUND
            strokeJoin = org.jetbrains.skia.PaintStrokeJoin.ROUND
            color = if (isCheck) 0xFF16A34A.toInt() else 0xFFDC2626.toInt()
        }
        val path = org.jetbrains.skia.Path()
        if (isCheck) {
            path.moveTo(20f, 52f)
            path.lineTo(40f, 72f)
            path.lineTo(76f, 26f)
        } else {
            path.moveTo(22f, 22f)
            path.lineTo(74f, 74f)
            path.moveTo(74f, 22f)
            path.lineTo(22f, 74f)
        }
        canvas.drawPath(path, paint)
        val bytes = surface.makeImageSnapshot().encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: return
        pendingStamp = PendingImageStamp(if (isCheck) "Checkmark" else "Cross", bytes, 0.055f)
        activeTool = ToolMode.STAMP
        notifyStatus("Click anywhere on a page to place ${if (isCheck) "✓" else "✗"}")
    }

    fun importSigningIdentity(label: String, p12File: File) {
        scope.launch {
            try {
                val raw = p12File.readBytes()
                val b64 = java.util.Base64.getEncoder().encodeToString(raw)
                val id = "id_${System.currentTimeMillis()}"
                check(VnylNative.saveSigningIdentity(id, b64)) { "Keychain store failed" }
                withContext(Dispatchers.Main) {
                    signingIdentities.add(SigningIdentityMeta(id, label.ifBlank { p12File.nameWithoutExtension }, p12File.name))
                    persistPrefs()
                    notifyStatus("Stored signing identity in Windows Credential Manager")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Import .p12 failed: ${t.message}", isError = true) }
            }
        }
    }

    fun deleteSigningIdentityItem(item: SigningIdentityMeta) {
        scope.launch {
            runCatching { VnylNative.deleteSigningIdentity(item.id) }
            withContext(Dispatchers.Main) {
                signingIdentities.removeAll { it.id == item.id }
                persistPrefs()
                notifyStatus("Removed signing identity ${item.label}")
            }
        }
    }

    fun jumpToPage(pageIndex: Int) {
        val tab = activeTab ?: return
        val clamped = pageIndex.coerceIn(0, (tab.pages.size - 1).coerceAtLeast(0))
        tab.currentPage = clamped
        scrollToPageTrigger = clamped to System.nanoTime()
    }

    fun updateZoom(newZoom: Float, mode: ZoomMode = ZoomMode.CUSTOM) {
        val tab = activeTab ?: return
        val clamped = newZoom.coerceIn(0.4f, 4.0f)
        val idx = activeTabIndex
        tabs[idx] = tab.copy(zoom = clamped, zoomMode = mode)
    }

    fun setCurrentPage(pageIndex: Int) {
        val tab = activeTab ?: return
        if (tab.currentPage != pageIndex && pageIndex in tab.pages.indices) {
            val idx = activeTabIndex
            tabs[idx] = tab.copy(currentPage = pageIndex)
        }
    }

    fun markDirty() {
        val tab = activeTab ?: return
        if (!tab.dirty) {
            tabs[activeTabIndex] = tab.copy(dirty = true)
        }
    }

    fun copySelectionToClipboard() {
        val sel = selection ?: return
        if (sel.text.isBlank()) return
        runCatching {
            val cb = Toolkit.getDefaultToolkit().systemClipboard
            cb.setContents(StringSelection(sel.text), null)
            notifyStatus("Copied ${sel.text.length} characters")
        }
    }

    fun commitHighlight(docId: Long, pageIndex: Int, rects: List<NormRect>) {
        if (rects.isEmpty()) return
        val argb = activeSwatch.argb
        scope.launch {
            try {
                for (r in rects) {
                    VnylNative.addHighlight(docId, pageIndex, r.left, r.top, r.right, r.bottom, argb)
                }
                withContext(Dispatchers.Main) {
                    invalidatePageRender(docId, pageIndex)
                    refreshUndoState(docId)
                    markDirty()
                    refreshAnnotations()
                    notifyStatus("Highlight added")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notifyStatus("Highlight failed: ${t.message}", isError = true)
                }
            }
        }
    }

    fun deleteAnnotationItem(item: AnnotationItemInfo) {
        val tab = activeTab ?: return
        scope.launch {
            try {
                VnylNative.deleteAnnotation(tab.docId, item.pageIndex, item.annotIndex)
                withContext(Dispatchers.Main) {
                    invalidatePageRender(tab.docId, item.pageIndex)
                    markDirty()
                    refreshAnnotations()
                    notifyStatus("Removed annotation")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notifyStatus("Delete annotation failed: ${t.message}", isError = true)
                }
            }
        }
    }

    fun commitInkStroke(docId: Long, pageIndex: Int, pointsNorm: List<Offset>) {
        if (pointsNorm.size < 2) return
        val flat = FloatArray(pointsNorm.size * 2)
        pointsNorm.forEachIndexed { idx, p ->
            flat[idx * 2] = p.x
            flat[idx * 2 + 1] = p.y
        }
        val argb = (activeSwatch.argb and 0x00FFFFFF) or (0xFF shl 24)
        scope.launch {
            try {
                VnylNative.addInkStroke(docId, pageIndex, flat, argb, 2.5f)
                withContext(Dispatchers.Main) {
                    invalidatePageRender(docId, pageIndex)
                    refreshUndoState(docId)
                    markDirty()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notifyStatus("Ink stroke failed: ${t.message}", isError = true)
                }
            }
        }
    }

    fun commitTextNote(docId: Long, pageIndex: Int, normX: Float, normY: Float, text: String) {
        if (text.isBlank()) return
        val argb = (activeSwatch.argb and 0x00FFFFFF) or (0xFF shl 24)
        scope.launch {
            try {
                VnylNative.addTextNote(docId, pageIndex, normX, normY, text, 13f, argb)
                withContext(Dispatchers.Main) {
                    invalidatePageRender(docId, pageIndex)
                    refreshUndoState(docId)
                    markDirty()
                    notifyStatus("Added note")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notifyStatus("Note failed: ${t.message}", isError = true)
                }
            }
        }
    }

    fun commitImageStamp(docId: Long, pageIndex: Int, normX: Float, normY: Float, stamp: PendingImageStamp) {
        scope.launch {
            try {
                VnylNative.addImageBytes(docId, pageIndex, stamp.pngBytes, normX, normY, stamp.normWidth)
                withContext(Dispatchers.Main) {
                    invalidatePageRender(docId, pageIndex)
                    refreshUndoState(docId)
                    markDirty()
                    pendingStamp = null
                    activeTool = ToolMode.SELECT
                    notifyStatus("Placed ${stamp.label}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notifyStatus("Image stamp failed: ${t.message}", isError = true)
                }
            }
        }
    }

    fun rotatePageAt(pageIdx: Int, deltaQuarters: Int) {
        val tab = activeTab ?: return
        scope.launch {
            try {
                val updated = VnylNative.rotatePage(tab.docId, pageIdx, deltaQuarters)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) tabs[idx] = tabs[idx].copy(pages = updated.pages, dirty = true)
                    invalidatePageRender(tab.docId, pageIdx)
                    refreshUndoState(tab.docId)
                    notifyStatus("Rotated page ${pageIdx + 1}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Rotate failed: ${t.message}", isError = true) }
            }
        }
    }

    fun rotateCurrentPage(deltaQuarters: Int) {
        val tab = activeTab ?: return
        rotatePageAt(tab.currentPage, deltaQuarters)
    }

    fun deletePageAt(pageIdx: Int) {
        val tab = activeTab ?: return
        if (tab.pages.size <= 1) {
            notifyStatus("Cannot delete the last page", isError = true)
            return
        }
        scope.launch {
            try {
                val updated = VnylNative.deletePage(tab.docId, pageIdx)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) {
                        tabs[idx] = tabs[idx].copy(
                            pages = updated.pages,
                            currentPage = pageIdx.coerceAtMost(updated.pageCount - 1),
                            dirty = true,
                        )
                    }
                    invalidatePageRender(tab.docId, null)
                    refreshUndoState(tab.docId)
                    refreshAnnotations()
                    notifyStatus("Deleted page ${pageIdx + 1}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Delete failed: ${t.message}", isError = true) }
            }
        }
    }

    fun deleteCurrentPage() {
        val tab = activeTab ?: return
        deletePageAt(tab.currentPage)
    }

    fun reorderPages(newOrder: List<Int>) {
        val tab = activeTab ?: return
        scope.launch {
            try {
                val updated = VnylNative.reorderPages(tab.docId, newOrder)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) tabs[idx] = tabs[idx].copy(pages = updated.pages, dirty = true)
                    invalidatePageRender(tab.docId, null)
                    refreshUndoState(tab.docId)
                    refreshAnnotations()
                    notifyStatus("Reordered ${updated.pageCount} pages")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Reorder failed: ${t.message}", isError = true) }
            }
        }
    }

    fun extractPages(pagesOneBased: String, destFile: File) {
        val tab = activeTab ?: return
        scope.launch {
            try {
                VnylNative.extractPagesToFile(tab.docId, pagesOneBased, destFile.absolutePath)
                withContext(Dispatchers.Main) {
                    notifyStatus("Extracted pages ($pagesOneBased) to ${destFile.name}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Extract failed: ${t.message}", isError = true) }
            }
        }
    }

    fun splitPdfByChunks(chunkSize: Int, outDir: File) {
        val tab = activeTab ?: return
        val base = tab.title.removeSuffix(".pdf").ifBlank { "document" }
        scope.launch {
            try {
                val count = VnylNative.splitDocument(tab.docId, tab.pages.size, chunkSize, outDir.absolutePath, base)
                withContext(Dispatchers.Main) {
                    notifyStatus("Split into $count PDF files in ${outDir.name}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Split failed: ${t.message}", isError = true) }
            }
        }
    }

    fun mergePdfIntoActive(sourceFile: File) {
        val tab = activeTab ?: return
        scope.launch {
            try {
                val updated = VnylNative.mergePdfFile(tab.docId, sourceFile.absolutePath)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) tabs[idx] = tabs[idx].copy(pages = updated.pages, dirty = true)
                    invalidatePageRender(tab.docId, null)
                    notifyStatus("Merged ${sourceFile.name} (${updated.pageCount} total pages)")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Merge failed: ${t.message}", isError = true) }
            }
        }
    }

    fun saveToPath(destFile: File) {
        val tab = activeTab ?: return
        if (tab.kind == DocumentKind.DOCX) {
            val docx = tab.docxDoc ?: return
            val updatedDoc = docx.copy(title = destFile.name)
            scope.launch {
                try {
                    VnylNative.saveDocx(updatedDoc, destFile.absolutePath)
                    withContext(Dispatchers.Main) {
                        val idx = tabs.indexOfFirst { it.docId == tab.docId }
                        if (idx >= 0) {
                            tabs[idx] = tabs[idx].copy(
                                filePath = destFile.absolutePath,
                                title = destFile.name,
                                docxDoc = updatedDoc,
                                dirty = false,
                            )
                        }
                        notifyStatus("Saved Word document ${destFile.name}")
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { notifyStatus("Save failed: ${t.message}", isError = true) }
                }
            }
            return
        }
        scope.launch {
            try {
                VnylNative.saveDocument(tab.docId, destFile.absolutePath)
                withContext(Dispatchers.Main) {
                    val idx = tabs.indexOfFirst { it.docId == tab.docId }
                    if (idx >= 0) {
                        tabs[idx] = tabs[idx].copy(
                            filePath = destFile.absolutePath,
                            title = destFile.name,
                            dirty = false,
                        )
                    }
                    notifyStatus("Saved ${destFile.name}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Save failed: ${t.message}", isError = true) }
            }
        }
    }

    fun exportPageAsImage(destFile: File, dpi: Int = 150) {
        val tab = activeTab ?: return
        val pageIdx = tab.currentPage
        scope.launch {
            try {
                VnylNative.exportPageImage(tab.docId, pageIdx, destFile.absolutePath, dpi)
                withContext(Dispatchers.Main) {
                    notifyStatus("Exported page ${pageIdx + 1} to ${destFile.name}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Image export failed: ${t.message}", isError = true) }
            }
        }
    }

    fun exportAsText(destFile: File, asMarkdown: Boolean) {
        val tab = activeTab ?: return
        scope.launch {
            try {
                VnylNative.exportDocumentText(tab.docId, tab.pages.size, tab.title, destFile.absolutePath, asMarkdown)
                withContext(Dispatchers.Main) { notifyStatus("Exported ${destFile.name}") }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Text export failed: ${t.message}", isError = true) }
            }
        }
    }

    fun printDocument() {
        val tab = activeTab ?: return
        scope.launch {
            try {
                val job = PrinterJob.getPrinterJob()
                job.jobName = tab.title
                job.setPrintable(
                    Printable { graphics, pageFormat, pageIndex ->
                        if (pageIndex >= tab.pages.size) return@Printable Printable.NO_SUCH_PAGE
                        val pagePts = tab.pages[pageIndex]
                        val pxW = (pagePts.widthPts * 2.0f).toInt().coerceIn(200, 2400)
                        val pxH = (pagePts.heightPts * 2.0f).toInt().coerceIn(200, 3200)
                        val skImg = VnylNative.renderPageToSkiaImage(tab.docId, pageIndex, pxW, pxH)
                        val bmp = org.jetbrains.skia.Bitmap.makeFromImage(skImg)
                        val rawBytes = bmp.readPixels() ?: return@Printable Printable.NO_SUCH_PAGE
                        val bufImg = BufferedImage(pxW, pxH, BufferedImage.TYPE_INT_ARGB)
                        val ints = IntArray(pxW * pxH)
                        for (i in ints.indices) {
                            val off = i * 4
                            val b = rawBytes[off].toInt() and 0xFF
                            val g = rawBytes[off + 1].toInt() and 0xFF
                            val r = rawBytes[off + 2].toInt() and 0xFF
                            val a = rawBytes[off + 3].toInt() and 0xFF
                            ints[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        }
                        bufImg.setRGB(0, 0, pxW, pxH, ints, 0, pxW)
                        val g2 = graphics as Graphics2D
                        g2.drawImage(
                            bufImg,
                            pageFormat.imageableX.toInt(),
                            pageFormat.imageableY.toInt(),
                            pageFormat.imageableWidth.toInt(),
                            pageFormat.imageableHeight.toInt(),
                            null,
                        )
                        Printable.PAGE_EXISTS
                    }
                )
                if (job.printDialog()) {
                    job.print()
                    withContext(Dispatchers.Main) { notifyStatus("Sent ${tab.title} to printer") }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { notifyStatus("Print failed: ${t.message}", isError = true) }
            }
        }
    }

    fun runSearch(query: String) {
        searchQuery = query
        val tab = activeTab ?: return
        if (query.isBlank()) {
            searchHits.clear()
            activeHitIndex = -1
            return
        }
        isSearching = true
        scope.launch {
            val hits = runCatching { VnylNative.searchDocument(tab.docId, query) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                searchHits.clear()
                searchHits.addAll(hits)
                activeHitIndex = if (hits.isNotEmpty()) 0 else -1
                isSearching = false
                hits.firstOrNull()?.let { jumpToPage(it.pageIndex) }
            }
        }
    }

    fun nextSearchHit(delta: Int) {
        if (searchHits.isEmpty()) return
        val next = (activeHitIndex + delta + searchHits.size) % searchHits.size
        activeHitIndex = next
        jumpToPage(searchHits[next].pageIndex)
    }

    fun clearSearch() {
        searchQuery = ""
        searchHits.clear()
        activeHitIndex = -1
    }

    private fun buildSamplePdf(): ByteArray {
        val pagesContent = listOf(
            """
            BT
            /F1 26 Tf 56 720 Td (Vnyl Studio - PDF + Word Workspace) Tj
            /F1 13 Tf 0 -32 Td (Single-Canvas Skiko Minimal Liquid Glass) Tj
            0 -28 Td (Try selecting this text with your mouse and pressing Ctrl+C to copy!) Tj
            0 -18 Td (Or switch tools: Highlight, Draw, Note, Stamp Signature / Image.) Tj
            0 -34 Td (Word Support: Open/Edit .docx files, Save .docx, or Convert to PDF.) Tj
            0 -18 Td (Security: PKCS#7 .p12 PDF signing + encrypted PDF password prompt.) Tj
            ET
            0.14 0.15 0.17 rg 56 470 500 110 re f
            0.84 0.85 0.86 rg 56 320 240 120 re f
            0.28 0.29 0.32 rg 316 320 240 120 re f
            BT 0.96 0.96 0.96 rg /F1 18 Tf 76 530 Td (Monochrome Refraction Reference Band) Tj ET
            """.trimIndent(),
            """
            BT
            /F1 24 Tf 56 720 Td (Page 2 - Complete Suite: Organize, Spreads, Word & Sign) Tj
            /F1 13 Tf 0 -30 Td (Ctrl+Shift+K Command Palette | Ctrl+P Print | Ctrl+\\ Split View) Tj
            0 -22 Td (- Page Organizer: reorder, rotate, delete, or extract pages) Tj
            0 -20 Td (- Two-Page Book Spread View + Draggable Page Scrubber) Tj
            0 -20 Td (- Edit .docx documents natively or export PDF -> .docx) Tj
            0 -20 Td (- Compare two PDFs side-by-side and cryptographically sign with .p12) Tj
            ET
            0.22 0.23 0.25 rg 56 460 500 90 re f
            0.76 0.77 0.79 rg 56 340 500 90 re f
            """.trimIndent()
        )

        val objects = mutableListOf<String>()
        objects.add("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        objects.add("2 0 obj << /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >> endobj\n")
        objects.add("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >> endobj\n")
        objects.add("4 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >> endobj\n")
        objects.add("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")
        pagesContent.forEachIndexed { idx, stream ->
            objects.add("${6 + idx} 0 obj << /Length ${stream.toByteArray(Charsets.US_ASCII).size} >> stream\n$stream\nendstream endobj\n")
        }

        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        for (obj in objects) {
            offsets.add(sb.length)
            sb.append(obj)
        }
        val xrefPos = sb.length
        sb.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (i in 1..objects.size) {
            sb.append(String.format("%010d 00000 n \n", offsets[i]))
        }
        sb.append("trailer << /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }
}
