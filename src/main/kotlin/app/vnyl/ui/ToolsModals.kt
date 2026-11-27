package app.vnyl.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vnyl.glass.GlassPalette
import app.vnyl.glass.LiquidGlassShaders
import app.vnyl.glass.LiquidGlassSurface
import app.vnyl.glass.MorphingSegmentedControl
import app.vnyl.native.VnylNative
import app.vnyl.state.DocumentKind
import app.vnyl.state.PendingImageStamp
import app.vnyl.state.ToolMode
import app.vnyl.state.VnylStore
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Path
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import java.io.File
import kotlin.math.roundToInt

/**
 * Full-window Liquid Glass Page Organizer: reorder, rotate, delete, or extract pages.
 */
@Composable
fun PageOrganizerModal(
    store: VnylStore,
    palette: GlassPalette,
    onPickExtractFile: (String) -> Unit,
) {
    val tab = store.activeTab ?: return
    val order = remember(tab.docId, tab.pages.size) {
        mutableStateListOf<Int>().apply { addAll(tab.pages.indices) }
    }
    val selected = remember(tab.docId, tab.pages.size) {
        mutableStateListOf<Int>()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable { store.organizerOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.84f)
                .clickable(enabled = false) {},
            cornerRadius = 24.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Page Organizer", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.text)
                        Text("${tab.title} • ${order.size} pages", fontSize = 12.sp, color = palette.textMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selected.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val oneBased = selected.sorted().joinToString(",") { "${it + 1}" }
                                    store.organizerOpen = false
                                    onPickExtractFile(oneBased)
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.activeFill, contentColor = palette.text),
                            ) {
                                Text("Extract Selected (${selected.size})", fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = {
                                store.reorderPages(order.toList())
                                store.organizerOpen = false
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.accentContent,
                            ),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Apply Order", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        SmallOverlayIconButton(onClick = { store.organizerOpen = false }, tint = palette.textMuted) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = palette.border)
                Spacer(Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(order, key = { pos, origIdx -> "${tab.docId}_${pos}_$origIdx" }) { pos, origIdx ->
                        val page = tab.pages.getOrNull(origIdx) ?: return@itemsIndexed
                        val aspect = (page.heightPts / page.widthPts.coerceAtLeast(1f)).coerceIn(0.6f, 1.8f)
                        val thumb = store.thumbImages[tab.docId to origIdx]
                        val isChecked = origIdx in selected

                        LaunchedEffect(tab.docId, origIdx, store.pageRevisions[tab.docId to origIdx]) {
                            store.requestThumbRender(tab.docId, origIdx, 180, (180 * aspect).roundToInt())
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isChecked) palette.activeFill else palette.hover)
                                .border(
                                    width = if (isChecked) 1.5.dp else 1.dp,
                                    color = if (isChecked) palette.accent else palette.border,
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Page ${origIdx + 1}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) selected.remove(origIdx) else selected.add(origIdx)
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height((110f * aspect).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .clickable {
                                        store.jumpToPage(origIdx)
                                        store.organizerOpen = false
                                    },
                            ) {
                                if (thumb != null) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawContext.canvas.nativeCanvas.drawImageRect(
                                            thumb,
                                            Rect.makeWH(thumb.width.toFloat(), thumb.height.toFloat()),
                                            Rect.makeWH(size.width, size.height),
                                            Paint().apply {
                                                colorFilter = LiquidGlassShaders.pdfColorFilter(store.pdfColorMode)
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                SmallOverlayIconButton(
                                    onClick = {
                                        if (pos > 0) {
                                            val moved = order.removeAt(pos)
                                            order.add(pos - 1, moved)
                                        }
                                    },
                                    enabled = pos > 0,
                                    tint = palette.text,
                                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move left", modifier = Modifier.size(15.dp)) }
                                SmallOverlayIconButton(
                                    onClick = {
                                        if (pos < order.lastIndex) {
                                            val moved = order.removeAt(pos)
                                            order.add(pos + 1, moved)
                                        }
                                    },
                                    enabled = pos < order.lastIndex,
                                    tint = palette.text,
                                ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move right", modifier = Modifier.size(15.dp)) }
                                SmallOverlayIconButton(
                                    onClick = { store.rotatePageAt(origIdx, 1) },
                                    tint = palette.text,
                                ) { Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(15.dp)) }
                                SmallOverlayIconButton(
                                    onClick = {
                                        if (order.size > 1) {
                                            order.removeAt(pos)
                                            store.deletePageAt(origIdx)
                                        }
                                    },
                                    enabled = order.size > 1,
                                    tint = palette.danger,
                                ) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(15.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Split PDF modal: split by fixed page chunks or extract a custom range.
 */
@Composable
fun SplitPdfModal(
    store: VnylStore,
    palette: GlassPalette,
    onPickChunkDir: (Int) -> Unit,
    onPickExtractFile: (String) -> Unit,
) {
    val tab = store.activeTab ?: return
    var chunkInput by remember { mutableStateOf("1") }
    var rangeInput by remember { mutableStateOf("1-${tab.pages.size.coerceAtMost(2)}") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { store.splitModalOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(440.dp).clickable(enabled = false) {},
            cornerRadius = 22.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Split & Extract PDF", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    SmallOverlayIconButton(onClick = { store.splitModalOpen = false }, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                OverlaySectionLabel("Extract Page Range (e.g. 1,3-5)", palette)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = rangeInput,
                        onValueChange = { rangeInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.hover)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Button(
                        onClick = {
                            store.splitModalOpen = false
                            onPickExtractFile(rangeInput.trim())
                        },
                        enabled = rangeInput.isNotBlank(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) { Text("Extract…", fontSize = 12.sp) }
                }

                HorizontalDivider(color = palette.border)

                OverlaySectionLabel("Split Every N Pages (${tab.pages.size} total pages)", palette)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = chunkInput,
                        onValueChange = { chunkInput = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier
                            .width(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.hover)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Button(
                        onClick = {
                            val n = (chunkInput.toIntOrNull() ?: 1).coerceAtLeast(1)
                            store.splitModalOpen = false
                            onPickChunkDir(n)
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.activeFill, contentColor = palette.text),
                    ) { Text("Choose Folder & Split…", fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * Signature Pad modal: Draw or type a signature, bake to transparent PNG, and stamp onto PDF.
 */
@Composable
fun SignatureModal(
    store: VnylStore,
    palette: GlassPalette,
    onUploadImageFile: () -> Unit,
) {
    var mode by remember { mutableStateOf(0) } // 0 = Draw, 1 = Type
    var typedName by remember { mutableStateOf("") }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val activeStroke = remember { mutableStateListOf<Offset>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { store.signatureModalOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(480.dp).clickable(enabled = false) {},
            cornerRadius = 22.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sign Document", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    SmallOverlayIconButton(onClick = { store.signatureModalOpen = false }, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                MorphingSegmentedControl(
                    items = listOf(0 to "Draw Signature", 1 to "Type Signature"),
                    selected = mode,
                    onSelect = { mode = it },
                    isDark = store.isDarkUi,
                    motionPreset = store.motionPreset,
                    accent = palette.accent,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (mode == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        activeStroke.clear()
                                        activeStroke.add(start)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activeStroke.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (activeStroke.size >= 2) strokes.add(activeStroke.toList())
                                        activeStroke.clear()
                                    },
                                )
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val skCanvas = drawContext.canvas.nativeCanvas
                            val paint = Paint().apply {
                                isAntiAlias = true
                                this.mode = PaintMode.STROKE
                                strokeWidth = 3.5f
                                strokeCap = PaintStrokeCap.ROUND
                                strokeJoin = PaintStrokeJoin.ROUND
                                color = 0xFF0F172A.toInt()
                            }
                            fun drawPts(pts: List<Offset>) {
                                if (pts.size < 2) return
                                val path = Path()
                                path.moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                                skCanvas.drawPath(path, paint)
                            }
                            strokes.forEach(::drawPts)
                            drawPts(activeStroke)
                        }
                        if (strokes.isEmpty() && activeStroke.isEmpty()) {
                            Text(
                                "Draw your signature here",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            singleLine = true,
                            textStyle = TextStyle(color = palette.text, fontSize = 14.sp),
                            cursorBrush = SolidColor(palette.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.hover)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            decorationBox = { inner ->
                                if (typedName.isEmpty()) Text("Type full name…", color = palette.textMuted, fontSize = 13.sp)
                                inner()
                            },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = typedName.ifBlank { "Signature Preview" },
                                fontSize = 30.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                color = if (typedName.isBlank()) Color(0xFFCBD5E1) else Color(0xFF0F172A),
                            )
                        }
                    }
                }

                if (store.savedSignatures.isNotEmpty()) {
                    OverlaySectionLabel("Saved Signatures", palette)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        store.savedSignatures.take(3).forEach { saved ->
                            Button(
                                onClick = {
                                    store.pendingStamp = saved
                                    store.activeTool = ToolMode.STAMP
                                    store.signatureModalOpen = false
                                    store.notifyStatus("Click anywhere on a page to place signature")
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.activeFill, contentColor = palette.text),
                            ) { Text(saved.label, fontSize = 11.5.sp) }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { strokes.clear(); activeStroke.clear(); typedName = "" },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                        ) { Text("Clear", fontSize = 12.sp) }
                        Button(
                            onClick = {
                                store.signatureModalOpen = false
                                onUploadImageFile()
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                        ) { Text("Upload Image…", fontSize = 12.sp) }
                    }
                    Button(
                        onClick = {
                            val png = if (mode == 0) {
                                renderStrokesToPng(strokes.toList(), 440, 160)
                            } else {
                                renderTypedSignatureToPng(typedName.trim(), 440, 130)
                            }
                            if (png != null) {
                                val label = if (mode == 1 && typedName.isNotBlank()) typedName.trim() else "Drawn Signature"
                                val stamp = PendingImageStamp(label, png, 0.28f)
                                if (store.savedSignatures.none { it.label == label }) {
                                    store.savedSignatures.add(0, stamp)
                                }
                                store.pendingStamp = stamp
                                store.activeTool = ToolMode.STAMP
                                store.signatureModalOpen = false
                                store.notifyStatus("Click anywhere on a PDF page to stamp signature")
                            }
                        },
                        enabled = (mode == 0 && strokes.isNotEmpty()) || (mode == 1 && typedName.isNotBlank()),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) { Text("Place on Page", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

private fun renderStrokesToPng(strokes: List<List<Offset>>, w: Int, h: Int): ByteArray? {
    if (strokes.isEmpty()) return null
    val surface = Surface.makeRasterN32Premul(w, h)
    val canvas = surface.canvas
    val paint = Paint().apply {
        isAntiAlias = true
        mode = PaintMode.STROKE
        strokeWidth = 4f
        strokeCap = PaintStrokeCap.ROUND
        strokeJoin = PaintStrokeJoin.ROUND
        color = 0xFF0F172A.toInt()
    }
    for (pts in strokes) {
        if (pts.size < 2) continue
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        canvas.drawPath(path, paint)
    }
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
}

private fun renderTypedSignatureToPng(name: String, w: Int, h: Int): ByteArray? {
    if (name.isBlank()) return null
    val surface = Surface.makeRasterN32Premul(w, h)
    val canvas = surface.canvas
    val typeface = FontMgr.default.matchFamiliesStyle(
        arrayOf("Georgia", "Times New Roman", "Segoe UI"),
        org.jetbrains.skia.FontStyle.BOLD_ITALIC,
    ) ?: Typeface.makeEmpty()
    val font = Font(typeface, 44f)
    val line = TextLine.make(name, font)
    val x = ((w - line.width) * 0.5f).coerceAtLeast(12f)
    val y = (h * 0.65f)
    canvas.drawTextLine(line, x, y, Paint().apply {
        isAntiAlias = true
        color = 0xFF0F172A.toInt()
    })
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
}

/**
 * Document Compare Modal: Compare any two open tabs side-by-side visually and by text diff.
 */
@Composable
fun CompareDocumentsModal(store: VnylStore, palette: GlassPalette) {
    if (store.tabs.isEmpty()) return
    var rightTabIdx by remember { mutableStateOf((store.activeTabIndex + 1) % store.tabs.size) }
    var pageIdx by remember { mutableStateOf(0) }
    var diffMode by remember { mutableStateOf(0) } // 0 = Visual, 1 = Text Diff

    val leftTab = store.activeTab ?: return
    val rightTab = store.tabs.getOrNull(rightTabIdx) ?: leftTab
    val maxPages = maxOf(leftTab.pages.size, rightTab.pages.size)

    val leftText = remember(leftTab.docId, pageIdx) {
        if (pageIdx < leftTab.pages.size) runCatching { VnylNative.getPageText(leftTab.docId, pageIdx) }.getOrDefault("") else ""
    }
    val rightText = remember(rightTab.docId, pageIdx) {
        if (pageIdx < rightTab.pages.size) runCatching { VnylNative.getPageText(rightTab.docId, pageIdx) }.getOrDefault("") else ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable { store.compareModalOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.86f)
                .clickable(enabled = false) {},
            cornerRadius = 24.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Compare Documents", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    MorphingSegmentedControl(
                        items = listOf(0 to "Visual Side-by-Side", 1 to "Text Diff"),
                        selected = diffMode,
                        onSelect = { diffMode = it },
                        isDark = store.isDarkUi,
                        motionPreset = store.motionPreset,
                        accent = palette.accent,
                        modifier = Modifier.width(280.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { pageIdx = (pageIdx - 1).coerceAtLeast(0) },
                            enabled = pageIdx > 0,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                        ) { Text("Prev", fontSize = 11.5.sp) }
                        Text("Page ${pageIdx + 1} / $maxPages", fontSize = 12.sp, color = palette.text)
                        Button(
                            onClick = { pageIdx = (pageIdx + 1).coerceAtMost(maxPages - 1) },
                            enabled = pageIdx < maxPages - 1,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                        ) { Text("Next", fontSize = 11.5.sp) }
                        if (store.tabs.size > 1) {
                            Button(
                                onClick = { rightTabIdx = (rightTabIdx + 1) % store.tabs.size },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.activeFill, contentColor = palette.text),
                            ) { Text("Target: ${rightTab.title}", fontSize = 11.5.sp) }
                        }
                        SmallOverlayIconButton(onClick = { store.compareModalOpen = false }, tint = palette.textMuted) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(color = palette.border)

                if (diffMode == 0) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf(leftTab to "Left: ${leftTab.title}", rightTab to "Right: ${rightTab.title}").forEach { (docTab, label) ->
                            val img = store.pageImages.entries
                                .firstOrNull { it.key.docId == docTab.docId && it.key.pageIndex == pageIdx }
                                ?.value
                            LaunchedEffect(docTab.docId, pageIdx) {
                                if (pageIdx < docTab.pages.size) {
                                    store.requestPageRender(docTab.docId, pageIdx, 520, 680)
                                }
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.textMuted)
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (img != null) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawContext.canvas.nativeCanvas.drawImageRect(
                                                img,
                                                Rect.makeWH(img.width.toFloat(), img.height.toFloat()),
                                                Rect.makeWH(size.width, size.height),
                                                Paint(),
                                            )
                                        }
                                    } else {
                                        Text(
                                            if (pageIdx < docTab.pages.size) "Rendering…" else "No page ${pageIdx + 1}",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val leftLines = remember(leftText) { leftText.lines() }
                    val rightLines = remember(rightText) { rightText.lines() }
                    val rightSet = remember(rightLines) { rightLines.toSet() }
                    val leftSet = remember(leftLines) { leftLines.toSet() }

                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.hover)
                                .padding(12.dp),
                        ) {
                            items(leftLines.size) { i ->
                                val line = leftLines[i]
                                val removed = line.isNotBlank() && line !in rightSet
                                Text(
                                    text = if (removed) "- $line" else "  $line",
                                    fontSize = 12.sp,
                                    color = if (removed) palette.danger else palette.text,
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.hover)
                                .padding(12.dp),
                        ) {
                            items(rightLines.size) { i ->
                                val line = rightLines[i]
                                val added = line.isNotBlank() && line !in leftSet
                                Text(
                                    text = if (added) "+ $line" else "  $line",
                                    fontSize = 12.sp,
                                    color = if (added) Color(0xFF30D158) else palette.text,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Document Properties & About Vnyl Modal.
 */
@Composable
fun DocumentPropertiesModal(store: VnylStore, palette: GlassPalette) {
    val meta = store.activeMetadata ?: return
    val rows = listOf(
        "Title" to meta.title.ifBlank { "—" },
        "Author" to meta.author.ifBlank { "—" },
        "Subject" to meta.subject.ifBlank { "—" },
        "Creator" to meta.creator.ifBlank { "—" },
        "Producer" to meta.producer.ifBlank { "—" },
        "Pages" to "${meta.pageCount}",
        "Engine" to "Vnyl 0.9.0 • Rust PDFium + Kotlin/Skiko",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { store.propertiesModalOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(440.dp).clickable(enabled = false) {},
            cornerRadius = 22.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Document Properties", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    SmallOverlayIconButton(onClick = { store.propertiesModalOpen = false }, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = palette.border)
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, fontSize = 12.sp, color = palette.textMuted)
                        Text(v, fontSize = 12.sp, color = palette.text, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Multi-file Combine PDFs modal: pick PDFs, reorder them, and merge into a new document tab.
 */
@Composable
fun CombinePdfsModal(
    store: VnylStore,
    palette: GlassPalette,
    onPickAddPdf: () -> File?,
) {
    val queue = remember { mutableStateListOf<File>() }
    var outputTitle by remember { mutableStateOf("Combined.pdf") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable { store.combineModalOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(500.dp).clickable(enabled = false) {},
            cornerRadius = 22.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Combine PDFs", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    SmallOverlayIconButton(onClick = { store.combineModalOpen = false }, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                BasicTextField(
                    value = outputTitle,
                    onValueChange = { outputTitle = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.hover)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.hover)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (queue.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                Text("Add 2 or more PDF files to combine", fontSize = 12.sp, color = palette.textMuted)
                            }
                        }
                    } else {
                        items(queue.size) { idx ->
                            val f = queue[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.activeFill.copy(alpha = 0.45f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${idx + 1}. ${f.name}", fontSize = 12.sp, color = palette.text, modifier = Modifier.weight(1f))
                                Row {
                                    SmallOverlayIconButton(
                                        onClick = { if (idx > 0) queue.add(idx - 1, queue.removeAt(idx)) },
                                        enabled = idx > 0,
                                        tint = palette.text,
                                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up", modifier = Modifier.size(14.dp)) }
                                    SmallOverlayIconButton(
                                        onClick = { if (idx < queue.lastIndex) queue.add(idx + 1, queue.removeAt(idx)) },
                                        enabled = idx < queue.lastIndex,
                                        tint = palette.text,
                                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Down", modifier = Modifier.size(14.dp)) }
                                    SmallOverlayIconButton(
                                        onClick = { queue.removeAt(idx) },
                                        tint = palette.danger,
                                    ) { Icon(Icons.Rounded.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { onPickAddPdf()?.let { queue.add(it) } },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.activeFill, contentColor = palette.text),
                    ) { Text("+ Add PDF…", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            val title = outputTitle.trim().ifBlank { "Combined.pdf" }
                            store.combineFiles(queue.toList(), title)
                            store.combineModalOpen = false
                        },
                        enabled = queue.size >= 2,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) { Text("Combine Into New Tab", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/**
 * OS Keychain Signing Identity (.p12) Manager backed by Rust `keyring`.
 */
@Composable
fun SigningIdentitiesModal(
    store: VnylStore,
    palette: GlassPalette,
    onPickSignedPdfDest: (String) -> File?,
    onPickP12: () -> File?,
) {
    var labelInput by remember { mutableStateOf("") }
    var selectedIdentityId by remember(store.signingIdentities.size) {
        mutableStateOf(store.signingIdentities.firstOrNull()?.id)
    }
    var p12Password by remember { mutableStateOf("") }
    var signReason by remember { mutableStateOf("Approved and signed via Vnyl") }
    val activePdf = store.activeTab?.takeIf { it.kind == DocumentKind.PDF }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable { store.keychainModalOpen = false },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(480.dp).clickable(enabled = false) {},
            cornerRadius = 22.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Digital Signing & Keychain (.p12)", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    SmallOverlayIconButton(onClick = { store.keychainModalOpen = false }, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "Imported .p12 certificates live in Windows Credential Manager via Rust keyring and produce PKCS#7 detached PDF signatures.",
                    fontSize = 11.5.sp,
                    color = palette.textMuted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.text, fontSize = 12.5.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.hover)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            if (labelInput.isEmpty()) Text("Identity label (optional)…", color = palette.textMuted, fontSize = 12.sp)
                            inner()
                        },
                    )
                    Button(
                        onClick = {
                            onPickP12()?.let { file ->
                                store.importSigningIdentity(labelInput.trim(), file)
                                labelInput = ""
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) { Text("Import .p12…", fontSize = 12.sp) }
                }

                if (store.signingIdentities.isEmpty()) {
                    Text("No saved identities yet.", fontSize = 12.sp, color = palette.textMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        store.signingIdentities.forEach { item ->
                            val isSelected = item.id == selectedIdentityId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) palette.activeFill else palette.hover)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) palette.accent else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { selectedIdentityId = item.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(item.label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = palette.text)
                                    Text(item.fileName, fontSize = 11.sp, color = palette.textMuted)
                                }
                                SmallOverlayIconButton(onClick = { store.deleteSigningIdentityItem(item) }, tint = palette.danger) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }

                    if (activePdf != null && selectedIdentityId != null) {
                        HorizontalDivider(color = palette.border)
                        OverlaySectionLabel("Sign Active PDF (PKCS#7 Detached)", palette)
                        BasicTextField(
                            value = p12Password,
                            onValueChange = { p12Password = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(color = palette.text, fontSize = 12.5.sp),
                            cursorBrush = SolidColor(palette.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.hover)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            decorationBox = { inner ->
                                if (p12Password.isEmpty()) Text(".p12 certificate password (leave blank if empty)…", color = palette.textMuted, fontSize = 11.5.sp)
                                inner()
                            },
                        )
                        BasicTextField(
                            value = signReason,
                            onValueChange = { signReason = it },
                            singleLine = true,
                            textStyle = TextStyle(color = palette.text, fontSize = 12.5.sp),
                            cursorBrush = SolidColor(palette.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.hover)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    val defaultName = "${activePdf.title.removeSuffix(".pdf")}-signed.pdf"
                                    val dest = onPickSignedPdfDest(defaultName) ?: return@Button
                                    val id = selectedIdentityId ?: return@Button
                                    store.signActivePdfCryptographic(id, p12Password, signReason, dest)
                                    store.keychainModalOpen = false
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accent,
                                    contentColor = palette.accentContent,
                                ),
                            ) {
                                Text("Cryptographically Sign PDF…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive AcroForm Text Field editor modal.
 */
@Composable
fun FormFieldEditModal(store: VnylStore, palette: GlassPalette) {
    val req = store.pendingFormEdit ?: return
    var textVal by remember(req) { mutableStateOf(req.field.value) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .clickable { store.pendingFormEdit = null },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(360.dp).clickable(enabled = false) {},
            cornerRadius = 20.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Edit Form Field: ${req.field.name.ifBlank { "Text" }}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                BasicTextField(
                    value = textVal,
                    onValueChange = { textVal = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.hover)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { store.pendingFormEdit = null },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                    ) { Text("Cancel", fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            store.updateFormField(req.docId, req.field, textVal, false)
                            store.pendingFormEdit = null
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                    ) { Text("Apply", fontSize = 12.sp) }
                }
            }
        }
    }
}
