package app.vnyl.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vnyl.glass.GlassActionPill
import app.vnyl.glass.GlassPalette
import app.vnyl.glass.LiquidGlassSurface
import app.vnyl.native.DocxBlock
import app.vnyl.native.DocxDocumentInfo
import app.vnyl.native.DocxRun
import app.vnyl.state.PdfColorMode
import app.vnyl.state.PdfTabState
import app.vnyl.state.VnylStore
import java.io.File

/**
 * Minimal architectural Word (.docx) editor sheet rendered on Vnyl's monochrome studio canvas.
 */
@Composable
fun DocxEditorViewport(
    store: VnylStore,
    tab: PdfTabState,
    palette: GlassPalette,
    topChromePaddingDp: Int,
    onExportPdfFile: (DocxDocumentInfo) -> Unit,
) {
    val doc = tab.docxDoc ?: return
    var focusedIndex by remember(tab.docId) { mutableStateOf(0.coerceAtMost((doc.blocks.size - 1).coerceAtLeast(0))) }
    val focusedBlock = doc.blocks.getOrNull(focusedIndex)

    val paperBg = when (store.pdfColorMode) {
        PdfColorMode.LIGHT -> Color(0xFFFFFFFF)
        PdfColorMode.DARK -> Color(0xFF17181C)
        PdfColorMode.SEPIA -> Color(0xFFF6F1E4)
    }
    val paperInk = when (store.pdfColorMode) {
        PdfColorMode.DARK -> Color(0xFFE8EAEE)
        else -> Color(0xFF1A1C20)
    }
    val paperMuted = when (store.pdfColorMode) {
        PdfColorMode.DARK -> Color(0xFF9498A1)
        else -> Color(0xFF6B707B)
    }
    val paperBorder = when (store.pdfColorMode) {
        PdfColorMode.DARK -> Color(0x22FFFFFF)
        else -> Color(0x16000000)
    }

    fun mutateBlocks(transform: (MutableList<DocxBlock>) -> Unit) {
        val next = doc.blocks.toMutableList()
        transform(next)
        if (next.isEmpty()) {
            next.add(
                DocxBlock(
                    id = "b_${System.nanoTime()}",
                    kind = "paragraph",
                    align = "left",
                    runs = listOf(DocxRun("")),
                )
            )
        }
        focusedIndex = focusedIndex.coerceIn(0, next.lastIndex)
        store.updateDocxDocument(doc.copy(blocks = next))
    }

    fun setFocusedKind(newKind: String) {
        val idx = focusedIndex.coerceIn(0, (doc.blocks.size - 1).coerceAtLeast(0))
        val target = doc.blocks.getOrNull(idx) ?: return
        if (target.kind == "table") return
        mutateBlocks { list ->
            list[idx] = target.copy(kind = newKind)
        }
    }

    fun toggleFocusedRunStyle(bold: Boolean = false, italic: Boolean = false, underline: Boolean = false) {
        val idx = focusedIndex.coerceIn(0, (doc.blocks.size - 1).coerceAtLeast(0))
        val target = doc.blocks.getOrNull(idx) ?: return
        if (target.kind == "table") return
        val mergedText = target.runs.joinToString("") { it.text }
        val first = target.runs.firstOrNull() ?: DocxRun("")
        val nextRun = first.copy(
            text = mergedText,
            bold = if (bold) !first.bold else first.bold,
            italic = if (italic) !first.italic else first.italic,
            underline = if (underline) !first.underline else first.underline,
        )
        mutateBlocks { list ->
            list[idx] = target.copy(runs = listOf(nextRun))
        }
    }

    fun setFocusedAlign(align: String) {
        val idx = focusedIndex.coerceIn(0, (doc.blocks.size - 1).coerceAtLeast(0))
        val target = doc.blocks.getOrNull(idx) ?: return
        mutateBlocks { list ->
            list[idx] = target.copy(align = align)
        }
    }

    fun insertBlockAfter(kind: String) {
        val insertAt = (focusedIndex + 1).coerceIn(0, doc.blocks.size)
        val newBlock = if (kind == "table") {
            DocxBlock(
                id = "tbl_${System.nanoTime()}",
                kind = "table",
                rows = listOf(
                    listOf("Column A", "Column B"),
                    listOf("Entry 1", "Entry 2"),
                ),
            )
        } else {
            DocxBlock(
                id = "blk_${System.nanoTime()}",
                kind = kind,
                align = "left",
                runs = listOf(DocxRun("", bold = kind.startsWith("h"))),
            )
        }
        mutateBlocks { list ->
            list.add(insertAt, newBlock)
        }
        focusedIndex = insertAt
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Floating monochrome formatting & conversion bar below chrome
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (topChromePaddingDp - 14).coerceAtLeast(8).dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = Modifier.widthIn(max = 1120.dp),
                cornerRadius = 16.dp,
                isDark = store.isDarkUi,
                style = store.uiStyle,
                glassLook = store.glassLook,
                refractionScale = store.refractionStrength,
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val curKind = focusedBlock?.kind ?: "paragraph"
                    val firstRun = focusedBlock?.runs?.firstOrNull()

                    listOf(
                        "paragraph" to "Body",
                        "h1" to "H1",
                        "h2" to "H2",
                        "h3" to "H3",
                    ).forEach { (kind, label) ->
                        GlassActionPill(
                            onClick = { setFocusedKind(kind) },
                            palette = palette,
                            motionPreset = store.motionPreset,
                            active = curKind == kind,
                            cornerRadius = 9.dp,
                        ) {
                            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(palette.border))

                    GlassActionPill(
                        onClick = { setFocusedKind(if (curKind == "bullet") "paragraph" else "bullet") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = curKind == "bullet",
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = "Bullet list", modifier = Modifier.size(15.dp))
                    }
                    GlassActionPill(
                        onClick = { setFocusedKind(if (curKind == "numbered") "paragraph" else "numbered") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = curKind == "numbered",
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.FormatListNumbered, contentDescription = "Numbered list", modifier = Modifier.size(15.dp))
                    }
                    GlassActionPill(
                        onClick = { setFocusedKind(if (curKind == "quote") "paragraph" else "quote") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = curKind == "quote",
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.FormatQuote, contentDescription = "Quote", modifier = Modifier.size(15.dp))
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(palette.border))

                    GlassActionPill(
                        onClick = { toggleFocusedRunStyle(bold = true) },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = firstRun?.bold == true,
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.FormatBold, contentDescription = "Bold", modifier = Modifier.size(15.dp))
                    }
                    GlassActionPill(
                        onClick = { toggleFocusedRunStyle(italic = true) },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = firstRun?.italic == true,
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(15.dp))
                    }
                    GlassActionPill(
                        onClick = { toggleFocusedRunStyle(underline = true) },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = firstRun?.underline == true,
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.FormatUnderlined, contentDescription = "Underline", modifier = Modifier.size(15.dp))
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(palette.border))

                    GlassActionPill(
                        onClick = { setFocusedAlign("left") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = (focusedBlock?.align ?: "left") == "left",
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.FormatAlignLeft, contentDescription = "Align Left", modifier = Modifier.size(14.dp))
                    }
                    GlassActionPill(
                        onClick = { setFocusedAlign("center") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = focusedBlock?.align == "center",
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.FormatAlignCenter, contentDescription = "Align Center", modifier = Modifier.size(14.dp))
                    }
                    GlassActionPill(
                        onClick = { setFocusedAlign("right") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        active = focusedBlock?.align == "right",
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.FormatAlignRight, contentDescription = "Align Right", modifier = Modifier.size(14.dp))
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(palette.border))

                    GlassActionPill(
                        onClick = { insertBlockAfter("paragraph") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Paragraph", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Block", fontSize = 11.5.sp)
                    }
                    GlassActionPill(
                        onClick = { insertBlockAfter("table") },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        cornerRadius = 9.dp,
                    ) {
                        Icon(Icons.Rounded.TableChart, contentDescription = "Add Table", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Table", fontSize = 11.5.sp)
                    }

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(palette.border))

                    Button(
                        onClick = { store.convertActiveDocxToPdfTab() },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Open as PDF", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    GlassActionPill(
                        onClick = { onExportPdfFile(doc) },
                        palette = palette,
                        motionPreset = store.motionPreset,
                        cornerRadius = 9.dp,
                    ) {
                        Text("Export PDF…", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }

                    if (store.officeConverter.available && tab.filePath != null) {
                        GlassActionPill(
                            onClick = { store.openWordViaOfficeLayout(File(tab.filePath)) },
                            palette = palette,
                            motionPreset = store.motionPreset,
                            cornerRadius = 9.dp,
                        ) {
                            Text("${store.officeConverter.engine} Layout", fontSize = 11.sp, color = palette.textMuted)
                        }
                    }
                }
            }
        }

        // Centered A4/Letter Word Sheet Surface
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 18.dp, bottom = 72.dp, start = 24.dp, end = 24.dp),
        ) {
            item(key = "word_sheet_${tab.docId}") {
                Column(
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(4.dp))
                        .background(paperBg, RoundedCornerShape(4.dp))
                        .border(0.75.dp, paperBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 52.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "${doc.title}${if (tab.dirty) " • Edited" else ""}",
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = paperMuted,
                    )
                    HorizontalDivider(color = paperBorder)
                    Spacer(Modifier.height(4.dp))

                    var listCounter = 0
                    doc.blocks.forEachIndexed { idx, block ->
                        if (block.kind == "numbered") listCounter++ else listCounter = 0
                        val isFocused = idx == focusedIndex

                        if (block.kind == "table") {
                            DocxTableBlockEditor(
                                block = block,
                                isFocused = isFocused,
                                paperInk = paperInk,
                                paperBorder = paperBorder,
                                paperMuted = paperMuted,
                                accent = palette.accent,
                                onFocus = { focusedIndex = idx },
                                onUpdateRows = { updatedRows ->
                                    mutateBlocks { list ->
                                        list[idx] = block.copy(rows = updatedRows)
                                    }
                                },
                                onDeleteBlock = {
                                    mutateBlocks { list -> list.removeAt(idx) }
                                },
                            )
                        } else {
                            val mergedText = block.runs.joinToString("") { it.text }
                            val firstRun = block.runs.firstOrNull() ?: DocxRun("")
                            val fontSize = when (block.kind) {
                                "h1" -> 22.sp
                                "h2" -> 17.sp
                                "h3" -> 14.sp
                                "quote" -> 13.sp
                                else -> 13.5.sp
                            }
                            val fontWeight = when {
                                block.kind.startsWith("h") -> FontWeight.SemiBold
                                firstRun.bold -> FontWeight.Bold
                                else -> FontWeight.Normal
                            }
                            val fontStyle = if (firstRun.italic || block.kind == "quote") FontStyle.Italic else FontStyle.Normal
                            val textDecoration = if (firstRun.underline) TextDecoration.Underline else TextDecoration.None
                            val textAlign = when (block.align) {
                                "center" -> TextAlign.Center
                                "right" -> TextAlign.End
                                else -> TextAlign.Start
                            }
                            val prefix = when (block.kind) {
                                "bullet" -> "•"
                                "numbered" -> "$listCounter."
                                "quote" -> "│"
                                else -> null
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isFocused) paperBorder.copy(alpha = 0.14f) else Color.Transparent
                                    )
                                    .clickable { focusedIndex = idx }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                if (prefix != null) {
                                    Text(
                                        text = prefix,
                                        fontSize = fontSize,
                                        color = paperMuted,
                                        modifier = Modifier.width(24.dp),
                                    )
                                }
                                BasicTextField(
                                    value = mergedText,
                                    onValueChange = { newText ->
                                        focusedIndex = idx
                                        val nextRun = firstRun.copy(text = newText)
                                        mutateBlocks { list ->
                                            list[idx] = block.copy(runs = listOf(nextRun))
                                        }
                                    },
                                    textStyle = TextStyle(
                                        color = if (block.kind == "quote") paperMuted else paperInk,
                                        fontSize = fontSize,
                                        fontWeight = fontWeight,
                                        fontStyle = fontStyle,
                                        textDecoration = textDecoration,
                                        textAlign = textAlign,
                                        lineHeight = fontSize * 1.45f,
                                    ),
                                    cursorBrush = SolidColor(palette.accent),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (mergedText.isEmpty()) {
                                            Text(
                                                text = when (block.kind) {
                                                    "h1" -> "Heading 1…"
                                                    "h2" -> "Heading 2…"
                                                    "h3" -> "Heading 3…"
                                                    "bullet" -> "List item…"
                                                    "numbered" -> "Numbered item…"
                                                    "quote" -> "Quote…"
                                                    else -> "Write paragraph…"
                                                },
                                                fontSize = fontSize,
                                                color = paperMuted.copy(alpha = 0.45f),
                                            )
                                        }
                                        inner()
                                    },
                                )

                                if (isFocused) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (idx > 0) {
                                            SmallOverlayIconButton(
                                                onClick = {
                                                    mutateBlocks { list ->
                                                        val item = list.removeAt(idx)
                                                        list.add(idx - 1, item)
                                                    }
                                                    focusedIndex = idx - 1
                                                },
                                                tint = paperMuted,
                                            ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(13.dp)) }
                                        }
                                        if (idx < doc.blocks.lastIndex) {
                                            SmallOverlayIconButton(
                                                onClick = {
                                                    mutateBlocks { list ->
                                                        val item = list.removeAt(idx)
                                                        list.add(idx + 1, item)
                                                    }
                                                    focusedIndex = idx + 1
                                                },
                                                tint = paperMuted,
                                            ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(13.dp)) }
                                        }
                                        SmallOverlayIconButton(
                                            onClick = {
                                                mutateBlocks { list -> list.removeAt(idx) }
                                            },
                                            tint = palette.danger,
                                        ) { Icon(Icons.Rounded.Delete, contentDescription = "Delete block", modifier = Modifier.size(13.dp)) }
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
private fun DocxTableBlockEditor(
    block: DocxBlock,
    isFocused: Boolean,
    paperInk: Color,
    paperBorder: Color,
    paperMuted: Color,
    accent: Color,
    onFocus: () -> Unit,
    onUpdateRows: (List<List<String>>) -> Unit,
    onDeleteBlock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (isFocused) accent.copy(alpha = 0.45f) else paperBorder, RoundedCornerShape(8.dp))
            .clickable { onFocus() }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        block.rows.forEachIndexed { rIdx, row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { cIdx, cellVal ->
                    BasicTextField(
                        value = cellVal,
                        onValueChange = { updatedCell ->
                            onFocus()
                            val nextRows = block.rows.mapIndexed { ri, r ->
                                if (ri != rIdx) r else r.mapIndexed { ci, v -> if (ci == cIdx) updatedCell else v }
                            }
                            onUpdateRows(nextRows)
                        },
                        textStyle = TextStyle(
                            color = paperInk,
                            fontSize = 12.sp,
                            fontWeight = if (rIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (rIdx == 0) paperBorder.copy(alpha = 0.25f) else Color.Transparent)
                            .border(0.5.dp, paperBorder, RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (isFocused) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "+ Row",
                    fontSize = 11.sp,
                    color = paperMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val cols = block.rows.firstOrNull()?.size?.coerceAtLeast(1) ?: 2
                            onUpdateRows(block.rows + listOf(List(cols) { "" }))
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    "+ Column",
                    fontSize = 11.sp,
                    color = paperMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onUpdateRows(block.rows.map { it + "" })
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    "Remove Table",
                    fontSize = 11.sp,
                    color = Color(0xFFC94A4A),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDeleteBlock() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
