package app.vnyl

import app.vnyl.glass.LiquidGlassShaders
import app.vnyl.native.VnylNative
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VnylCoreSmokeTest {

    @Test
    fun `sksl liquid glass shaders compile and render on skiko surface`() {
        val surface = Surface.makeRasterN32Premul(220, 140)
        val canvas = surface.canvas
        val darkStudioBg = LiquidGlassShaders.createMinimalCanvasShader(220f, 140f, isDark = true)
        canvas.drawRect(Rect.makeWH(220f, 140f), Paint().apply { shader = darkStudioBg })
        val lightStudioBg = LiquidGlassShaders.createMinimalCanvasShader(220f, 140f, isDark = false)
        assertNotNull(lightStudioBg)

        val filter = LiquidGlassShaders.createGlassBackdropFilter(
            widthPx = 190f,
            heightPx = 90f,
            cornerRadiusPx = 20f,
            blurSigma = 12f,
            refractStrength = 1.0f,
            chromaticAberration = 1.0f,
            tintR = 0.95f,
            tintG = 0.97f,
            tintB = 1.0f,
            tintA = 0.45f,
            highlightAlpha = 1.0f,
            isDark = false,
            pointerLocalX = 80f,
            pointerLocalY = 40f,
            pointerActive = 1f,
        )
        canvas.save()
        canvas.translate(15f, 25f)
        canvas.clipRRect(RRect.makeXYWH(0f, 0f, 190f, 90f, 20f), true)
        canvas.saveLayer(Canvas.SaveLayerRec(Rect.makeWH(190f, 90f), null, filter))
        canvas.restore()
        canvas.restore()

        val morphBuilder = RuntimeShaderBuilder(LiquidGlassShaders.morphPillEffect).apply {
            uniform("size", 100f, 32f)
            uniform("cornerRadius", 16f)
            uniform("baseColor", 1f, 1f, 1f, 0.85f)
            uniform("glowColor", 0.04f, 0.52f, 1f, 0.5f)
            uniform("velocityNorm", 0.35f)
            uniform("timeSec", 1.2f)
        }
        val morphShader = morphBuilder.makeShader()
        canvas.drawRect(Rect.makeXYWH(20f, 40f, 100f, 32f), Paint().apply { shader = morphShader })

        val snap = surface.makeImageSnapshot()
        assertNotNull(snap)
        assertEquals(220, snap.width)
        assertEquals(140, snap.height)
    }

    @Test
    fun `rust pdfium jni bridge opens edits stamps reorders splits and exports pdf`() {
        val samplePdf = buildValidMultiPagePdf(3)

        val info = VnylNative.openDocumentBytes(samplePdf, "test.pdf")
        val tmpDir = createTempDirectory("vnyl_jni_phase3").toFile()
        try {
            assertEquals(3, info.pageCount)
            val img = VnylNative.renderPageToSkiaImage(info.docId, 0, 128, 128)
            assertEquals(128, img.width)

            val spans = VnylNative.getPageTextSpans(info.docId, 0)
            assertTrue(spans.isNotEmpty(), "Expected text spans")

            assertTrue(VnylNative.addHighlight(info.docId, 0, 0.1f, 0.4f, 0.6f, 0.5f, 0x99FFEB3B.toInt()))
            val annots = VnylNative.getAnnotations(info.docId)
            assertTrue(annots.isNotEmpty())
            assertTrue(VnylNative.deleteAnnotation(info.docId, 0, annots.first().annotIndex))

            assertTrue(VnylNative.addInkStroke(info.docId, 0, floatArrayOf(0.1f, 0.2f, 0.5f, 0.3f), 0xFF0A84FF.toInt(), 2.5f))
            assertTrue(VnylNative.addTextNote(info.docId, 0, 0.2f, 0.7f, "Note", 12f, 0xFFE11D48.toInt()))

            // Stamp PNG signature/image onto page 0
            val sigSurf = Surface.makeRasterN32Premul(32, 16)
            sigSurf.canvas.drawRect(Rect.makeWH(32f, 16f), Paint().apply { color = 0xFF0A84FF.toInt() })
            val pngBytes = sigSurf.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
            assertTrue(VnylNative.addImageBytes(info.docId, 0, pngBytes, 0.2f, 0.3f, 0.25f))

            // Reorder [2, 0, 1]
            val reordered = VnylNative.reorderPages(info.docId, listOf(2, 0, 1))
            assertEquals(3, reordered.pageCount)
            assertTrue(VnylNative.getPageText(info.docId, 0).contains("Page 3"))

            // Extract & Split
            val extracted = File(tmpDir, "extracted.pdf")
            assertTrue(VnylNative.extractPagesToFile(info.docId, "1-2", extracted.absolutePath))
            assertTrue(extracted.length() > 100)

            val splitDir = File(tmpDir, "split")
            val chunkCount = VnylNative.splitDocument(info.docId, 3, 2, splitDir.absolutePath, "part")
            assertEquals(2, chunkCount)

            val meta = VnylNative.getMetadata(info.docId)
            assertEquals(3, meta.pageCount)

            // Phase 4: Undo/Redo & Form Fields & Combine
            val afterDel = VnylNative.deletePage(info.docId, 2)
            assertEquals(2, afterDel.pageCount)
            val undone = VnylNative.undoDocument(info.docId)
            assertEquals(3, undone.pageCount)
            val redone = VnylNative.redoDocument(info.docId)
            assertEquals(2, redone.pageCount)

            val formFields = VnylNative.getFormFields(info.docId, 0)
            assertTrue(formFields.isEmpty())

            val combined = VnylNative.combinePdfFiles(listOf(extracted.absolutePath, extracted.absolutePath), "Merged.pdf")
            assertEquals(4, combined.pageCount)
            assertTrue(VnylNative.closeDocument(combined.docId))

            // Phase 5: DOCX roundtrip, PDF <-> DOCX conversion, Undo state & Office detector
            val undoState = VnylNative.getUndoState(info.docId)
            assertTrue(undoState.first)
            val exportedDocx = File(tmpDir, "exported.docx")
            assertTrue(VnylNative.exportPdfToDocx(info.docId, 2, "Test.pdf", exportedDocx.absolutePath))
            assertTrue(exportedDocx.length() > 100)
            val parsedDocx = VnylNative.parseDocx(exportedDocx.absolutePath)
            assertTrue(parsedDocx.blocks.isNotEmpty())
            val editedDocx = parsedDocx.copy(title = "Edited.docx")
            val savedDocx = File(tmpDir, "edited.docx")
            assertTrue(VnylNative.saveDocx(editedDocx, savedDocx.absolutePath))
            val pdfFromDocx = VnylNative.convertDocxToPdfTab(editedDocx)
            assertTrue(pdfFromDocx.pageCount >= 1)
            assertTrue(VnylNative.closeDocument(pdfFromDocx.docId))
            val convInfo = VnylNative.detectOfficeConverter()
            assertTrue(convInfo.engine.isNotBlank())
        } finally {
            tmpDir.deleteRecursively()
            assertTrue(VnylNative.closeDocument(info.docId))
        }
    }

    private fun buildValidMultiPagePdf(pages: Int): ByteArray {
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        val kids = (0 until pages).joinToString(" ") { "${4 + it * 2} 0 R" }

        offsets.add(sb.length)
        sb.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")

        offsets.add(sb.length)
        sb.append("2 0 obj << /Type /Pages /Kids [$kids] /Count $pages >> endobj\n")

        offsets.add(sb.length)
        sb.append("3 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")

        for (i in 0 until pages) {
            val pageId = 4 + i * 2
            val contentId = pageId + 1
            offsets.add(sb.length)
            sb.append("$pageId 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 240 240] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentId 0 R >> endobj\n")
            val stream = "BT /F1 14 Tf 20 120 Td (Hello Liquid Glass Page ${i + 1}) Tj ET"
            offsets.add(sb.length)
            sb.append("$contentId 0 obj << /Length ${stream.toByteArray(Charsets.US_ASCII).size} >> stream\n$stream\nendstream endobj\n")
        }

        val total = offsets.size + 1
        val xref = sb.length
        sb.append("xref\n0 $total\n0000000000 65535 f \n")
        for (off in offsets) sb.append(String.format("%010d 00000 n \n", off))
        sb.append("trailer << /Size $total /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }
}
