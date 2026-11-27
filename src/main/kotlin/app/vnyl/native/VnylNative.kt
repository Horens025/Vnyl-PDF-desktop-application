package app.vnyl.native

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer

@Serializable
data class PageSizeInfo(
    @SerialName("width_pts") val widthPts: Float,
    @SerialName("height_pts") val heightPts: Float,
)

@Serializable
data class DocumentOpenInfo(
    @SerialName("doc_id") val docId: Long,
    val title: String,
    @SerialName("page_count") val pageCount: Int,
    val pages: List<PageSizeInfo>,
)

@Serializable
data class DocumentMetadataInfo(
    val title: String,
    val author: String = "",
    val subject: String = "",
    val creator: String = "",
    val producer: String = "",
    @SerialName("page_count") val pageCount: Int = 0,
)

@Serializable
data class OutlineItemInfo(
    val title: String,
    @SerialName("page_index") val pageIndex: Int? = null,
    val children: List<OutlineItemInfo> = emptyList(),
)

@Serializable
data class SearchHitInfo(
    @SerialName("page_index") val pageIndex: Int,
    @SerialName("match_text") val matchText: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class TextSpanInfo(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class PageLinkInfo(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val uri: String? = null,
    @SerialName("dest_page") val destPage: Int? = null,
)

@Serializable
data class AnnotationItemInfo(
    @SerialName("page_index") val pageIndex: Int,
    @SerialName("annot_index") val annotIndex: Int,
    val kind: String,
    val contents: String = "",
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class FormFieldInfo(
    @SerialName("page_index") val pageIndex: Int,
    @SerialName("annot_index") val annotIndex: Int,
    val kind: String,
    val name: String = "",
    val value: String = "",
    val checked: Boolean = false,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class DocxRun(
    val text: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

@Serializable
data class DocxBlock(
    val id: String,
    val kind: String = "paragraph",
    val align: String = "left",
    val runs: List<DocxRun> = emptyList(),
    val rows: List<List<String>> = emptyList(),
)

@Serializable
data class DocxDocumentInfo(
    val title: String,
    val blocks: List<DocxBlock> = emptyList(),
)

@Serializable
data class OfficeConverterInfo(
    val available: Boolean = false,
    val engine: String = "Built-in",
    val path: String? = null,
)

object VnylNative {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val coreName = System.mapLibraryName("vnyl_core")
            val pdfiumName = System.mapLibraryName("pdfium")

            val candidates = listOfNotNull(
                System.getProperty("vnyl.native.dir")?.takeIf { !it.contains("\$APPDIR") }?.let { File(it) },
                System.getProperty("compose.application.resources.dir")?.let { File(it) },
                File("native/win-x64"),
                File("native/resources/windows-x64"),
                File("target/release"),
                File("."),
            )

            val nativeDir = candidates.firstOrNull { File(it, coreName).exists() }
                ?: candidates.firstOrNull { it.isDirectory }
                ?: File("native/win-x64")

            val pdfiumFile = File(nativeDir, pdfiumName)
            if (pdfiumFile.exists()) {
                System.load(pdfiumFile.absolutePath)
            }

            val coreFile = File(nativeDir, coreName)
            if (coreFile.exists()) {
                System.load(coreFile.absolutePath)
            } else {
                System.loadLibrary("vnyl_core")
            }

            check(initPdfium(nativeDir.absolutePath)) {
                "Failed to initialize PDFium from ${nativeDir.absolutePath}"
            }
            loaded = true
        }
    }

    @JvmStatic
    private external fun initPdfium(hintDir: String): Boolean

    @JvmStatic
    private external fun openDocumentJson(path: String, password: String?): String

    @JvmStatic
    private external fun openDocumentBytesJson(bytes: ByteArray, name: String): String

    @JvmStatic
    external fun closeDocument(docId: Long): Boolean

    @JvmStatic
    private external fun renderPageIntoBuffer(
        docId: Long,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        outBuffer: ByteBuffer,
    ): Boolean

    @JvmStatic
    private external fun getOutlineJson(docId: Long): String

    @JvmStatic
    private external fun getMetadataJson(docId: Long): String

    @JvmStatic
    external fun getPageText(docId: Long, pageIndex: Int): String

    @JvmStatic
    private external fun getPageTextSpansJson(docId: Long, pageIndex: Int): String

    @JvmStatic
    private external fun getPageLinksJson(docId: Long, pageIndex: Int): String

    @JvmStatic
    private external fun getAnnotationsJson(docId: Long): String

    @JvmStatic
    external fun deleteAnnotation(docId: Long, pageIndex: Int, annotIndex: Int): Boolean

    @JvmStatic
    private external fun searchDocumentJson(docId: Long, query: String, maxHits: Int): String

    @JvmStatic
    external fun addHighlight(
        docId: Long,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        argb: Int,
    ): Boolean

    @JvmStatic
    external fun addInkStroke(
        docId: Long,
        pageIndex: Int,
        xyCoords: FloatArray,
        argb: Int,
        strokeWidthPts: Float,
    ): Boolean

    @JvmStatic
    external fun addTextNote(
        docId: Long,
        pageIndex: Int,
        normX: Float,
        normY: Float,
        text: String,
        fontSizePts: Float,
        argb: Int,
    ): Boolean

    @JvmStatic
    external fun addImageBytes(
        docId: Long,
        pageIndex: Int,
        imageBytes: ByteArray,
        normX: Float,
        normY: Float,
        normWidth: Float,
    ): Boolean

    @JvmStatic
    private external fun rotatePageJson(docId: Long, pageIndex: Int, deltaQuarters: Int): String

    @JvmStatic
    private external fun deletePageJson(docId: Long, pageIndex: Int): String

    @JvmStatic
    private external fun reorderPagesJson(docId: Long, order: IntArray): String

    @JvmStatic
    external fun extractPagesToFile(docId: Long, pagesOneBased: String, destPath: String): Boolean

    @JvmStatic
    external fun splitDocument(
        docId: Long,
        pageCount: Int,
        chunkSize: Int,
        outDir: String,
        baseName: String,
    ): Int

    @JvmStatic
    private external fun mergePdfFileJson(docId: Long, sourcePath: String): String

    @JvmStatic
    external fun saveDocument(docId: Long, destPath: String): Boolean

    @JvmStatic
    external fun exportPageImage(docId: Long, pageIndex: Int, destPath: String, dpi: Int): Boolean

    @JvmStatic
    external fun exportDocumentText(
        docId: Long,
        pageCount: Int,
        title: String,
        destPath: String,
        asMarkdown: Boolean,
    ): Boolean

    @JvmStatic
    private external fun undoDocumentJson(docId: Long): String

    @JvmStatic
    private external fun redoDocumentJson(docId: Long): String

    @JvmStatic
    private external fun combinePdfFilesJson(pathsJoined: String, title: String): String

    @JvmStatic
    private external fun getFormFieldsJson(docId: Long, pageIndex: Int): String

    @JvmStatic
    external fun setFormField(
        docId: Long,
        pageIndex: Int,
        annotIndex: Int,
        value: String,
        checked: Boolean,
    ): Boolean

    @JvmStatic
    external fun saveSigningIdentity(id: String, p12Base64: String): Boolean

    @JvmStatic
    external fun loadSigningIdentity(id: String): String

    @JvmStatic
    external fun deleteSigningIdentity(id: String): Boolean

    @JvmStatic
    private external fun getUndoStateMask(docId: Long): Int

    @JvmStatic
    private external fun pickFolderNative(title: String): String

    @JvmStatic
    private external fun parseDocxJson(path: String): String

    @JvmStatic
    private external fun saveDocxFromJson(docxJson: String, destPath: String): Boolean

    @JvmStatic
    private external fun convertDocxJsonToPdfJson(docxJson: String): String

    @JvmStatic
    private external fun exportDocxJsonToPdfFile(docxJson: String, destPath: String): Boolean

    @JvmStatic
    private external fun detectOfficeConverterJson(): String

    @JvmStatic
    private external fun convertWordViaOfficeToPdfJson(path: String): String

    @JvmStatic
    external fun exportPdfToDocx(
        docId: Long,
        pageCount: Int,
        title: String,
        destPath: String,
    ): Boolean

    @JvmStatic
    external fun signPdfPkcs12ToFile(
        docId: Long,
        identityId: String,
        password: String,
        reason: String,
        destPath: String,
    ): Boolean

    fun openDocument(path: String, password: String? = null): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(openDocumentJson(path, password))
    }

    fun openDocumentBytes(bytes: ByteArray, name: String): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(openDocumentBytesJson(bytes, name))
    }

    fun getOutline(docId: Long): List<OutlineItemInfo> {
        ensureLoaded()
        return json.decodeFromString(getOutlineJson(docId))
    }

    fun getMetadata(docId: Long): DocumentMetadataInfo {
        ensureLoaded()
        return json.decodeFromString(getMetadataJson(docId))
    }

    fun getPageTextSpans(docId: Long, pageIndex: Int): List<TextSpanInfo> {
        ensureLoaded()
        return json.decodeFromString(getPageTextSpansJson(docId, pageIndex))
    }

    fun getPageLinks(docId: Long, pageIndex: Int): List<PageLinkInfo> {
        ensureLoaded()
        return json.decodeFromString(getPageLinksJson(docId, pageIndex))
    }

    fun getAnnotations(docId: Long): List<AnnotationItemInfo> {
        ensureLoaded()
        return json.decodeFromString(getAnnotationsJson(docId))
    }

    fun searchDocument(docId: Long, query: String, maxHits: Int = 250): List<SearchHitInfo> {
        ensureLoaded()
        return json.decodeFromString(searchDocumentJson(docId, query, maxHits))
    }

    fun rotatePage(docId: Long, pageIndex: Int, deltaQuarters: Int): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(rotatePageJson(docId, pageIndex, deltaQuarters))
    }

    fun deletePage(docId: Long, pageIndex: Int): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(deletePageJson(docId, pageIndex))
    }

    fun reorderPages(docId: Long, order: List<Int>): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(reorderPagesJson(docId, order.toIntArray()))
    }

    fun mergePdfFile(docId: Long, sourcePath: String): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(mergePdfFileJson(docId, sourcePath))
    }

    fun undoDocument(docId: Long): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(undoDocumentJson(docId))
    }

    fun redoDocument(docId: Long): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(redoDocumentJson(docId))
    }

    fun combinePdfFiles(paths: List<String>, title: String): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(combinePdfFilesJson(paths.joinToString("\n"), title))
    }

    fun getFormFields(docId: Long, pageIndex: Int): List<FormFieldInfo> {
        ensureLoaded()
        return json.decodeFromString(getFormFieldsJson(docId, pageIndex))
    }

    fun getUndoState(docId: Long): Pair<Boolean, Boolean> {
        ensureLoaded()
        val mask = getUndoStateMask(docId)
        return ((mask and 1) != 0) to ((mask and 2) != 0)
    }

    fun pickFolder(title: String): File? {
        ensureLoaded()
        val raw = pickFolderNative(title)
        return raw.takeIf { it.isNotBlank() }?.let { File(it) }
    }

    fun parseDocx(path: String): DocxDocumentInfo {
        ensureLoaded()
        return json.decodeFromString(parseDocxJson(path))
    }

    fun saveDocx(doc: DocxDocumentInfo, destPath: String): Boolean {
        ensureLoaded()
        return saveDocxFromJson(json.encodeToString(DocxDocumentInfo.serializer(), doc), destPath)
    }

    fun convertDocxToPdfTab(doc: DocxDocumentInfo): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(
            convertDocxJsonToPdfJson(json.encodeToString(DocxDocumentInfo.serializer(), doc))
        )
    }

    fun exportDocxToPdfFile(doc: DocxDocumentInfo, destPath: String): Boolean {
        ensureLoaded()
        return exportDocxJsonToPdfFile(
            json.encodeToString(DocxDocumentInfo.serializer(), doc),
            destPath,
        )
    }

    fun detectOfficeConverter(): OfficeConverterInfo {
        ensureLoaded()
        return json.decodeFromString(detectOfficeConverterJson())
    }

    fun convertWordViaOfficeToPdfTab(path: String): DocumentOpenInfo {
        ensureLoaded()
        return json.decodeFromString(convertWordViaOfficeToPdfJson(path))
    }

    fun renderPageToSkiaImage(
        docId: Long,
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
    ): Image {
        ensureLoaded()
        val byteCount = widthPx * heightPx * 4
        val direct = ByteBuffer.allocateDirect(byteCount)
        val ok = renderPageIntoBuffer(docId, pageIndex, widthPx, heightPx, direct)
        check(ok) { "PDFium render failed for doc=$docId page=$pageIndex" }

        val bytes = ByteArray(byteCount)
        direct.rewind()
        direct.get(bytes)

        val info = ImageInfo(
            width = widthPx,
            height = heightPx,
            colorType = ColorType.BGRA_8888,
            alphaType = ColorAlphaType.PREMUL,
            colorSpace = ColorSpace.sRGB,
        )
        return Image.makeRaster(info, bytes, widthPx * 4)
    }
}
