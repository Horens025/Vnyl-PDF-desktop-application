package app.vnyl.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vnyl.glass.LiquidGlassShaders
import app.vnyl.native.PageLinkInfo
import app.vnyl.native.PageSizeInfo
import app.vnyl.native.SearchHitInfo
import app.vnyl.native.TextSpanInfo
import app.vnyl.state.ContextMenuState
import app.vnyl.state.NormRect
import app.vnyl.state.PageRenderKey
import app.vnyl.state.PdfColorMode
import app.vnyl.state.PdfTabState
import app.vnyl.state.PendingFormEdit
import app.vnyl.state.PendingNoteRequest
import app.vnyl.state.TextSelectionState
import app.vnyl.state.ToolMode
import app.vnyl.state.VnylStore
import app.vnyl.state.ZoomMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Path
import org.jetbrains.skia.Rect
import java.awt.Cursor
import java.awt.Desktop
import java.net.URI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PdfViewport(
    store: VnylStore,
    tab: PdfTabState,
    topChromePaddingDp: Int,
    leftInsetDp: Int,
    isPrimary: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val hScrollState = rememberScrollState()
    val density = LocalDensity.current
    val uiScope = rememberCoroutineScope()
    var hoveredLink by remember { mutableStateOf<PageLinkInfo?>(null) }
    var scrubbingPage by remember { mutableStateOf<Int?>(null) }

    val useSpread = isPrimary && store.twoPageSpread && tab.pages.size > 1
    val rowIndices = remember(tab.pages.size, useSpread) {
        if (useSpread) {
            (tab.pages.indices step 2).map { i ->
                listOfNotNull(
                    i.takeIf { it < tab.pages.size },
                    (i + 1).takeIf { it < tab.pages.size },
                )
            }
        } else {
            tab.pages.indices.map { listOf(it) }
        }
    }

    if (isPrimary) {
        LaunchedEffect(store.scrollToPageTrigger, useSpread) {
            val trigger = store.scrollToPageTrigger ?: return@LaunchedEffect
            val pageTarget = trigger.first.coerceIn(0, (tab.pages.size - 1).coerceAtLeast(0))
            val rowTarget = if (useSpread) pageTarget / 2 else pageTarget
            listState.animateScrollToItem(rowTarget.coerceIn(0, (rowIndices.size - 1).coerceAtLeast(0)))
        }

        val visiblePage by remember(listState, tab.docId, useSpread) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val rowIdx = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - center)
                }?.index ?: 0
                if (useSpread) (rowIdx * 2).coerceIn(0, (tab.pages.size - 1).coerceAtLeast(0)) else rowIdx
            }
        }

        LaunchedEffect(visiblePage) {
            store.setCurrentPage(visiblePage)
        }

        LaunchedEffect(store.autoScrollActive, store.autoScrollSpeed) {
            if (!store.autoScrollActive) return@LaunchedEffect
            var lastNs = 0L
            while (store.autoScrollActive) {
                withFrameNanos { now ->
                    if (lastNs != 0L) {
                        val dt = ((now - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                        uiScope.launch { listState.scrollBy(store.autoScrollSpeed * dt) }
                    }
                    lastNs = now
                }
            }
        }
    }

    val colorFilter = remember(store.pdfColorMode) {
        LiquidGlassShaders.pdfColorFilter(store.pdfColorMode)
    }

    val cursorIcon = when {
        hoveredLink != null && store.activeTool == ToolMode.SELECT -> PointerIcon(Cursor(Cursor.HAND_CURSOR))
        store.activeTool == ToolMode.SELECT -> PointerIcon(Cursor(Cursor.TEXT_CURSOR))
        store.activeTool == ToolMode.PAN -> PointerIcon(Cursor(Cursor.HAND_CURSOR))
        else -> PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerHoverIcon(cursorIcon)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (event.keyboardModifiers.isCtrlPressed) {
                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (deltaY != 0f) {
                        val factor = if (deltaY < 0f) 1.1f else 0.9f
                        store.updateZoom(tab.zoom * factor, ZoomMode.CUSTOM)
                    }
                }
            },
    ) {
        val availableWidthDp = (maxWidth.value - leftInsetDp - 64f).coerceAtLeast(200f)
        val availableHeightDp = (maxHeight.value - topChromePaddingDp - 48f).coerceAtLeast(200f)
        val spreadFactor = if (useSpread) 2.04f else 1f

        val effectiveZoom = when (tab.zoomMode) {
            ZoomMode.FIT_WIDTH -> {
                val p = tab.pages.getOrNull(tab.currentPage) ?: tab.pages.firstOrNull()
                val ptsW = (p?.widthPts ?: 612f).coerceAtLeast(1f) * spreadFactor
                (availableWidthDp / ptsW).coerceIn(0.35f, 3.5f)
            }
            ZoomMode.FIT_PAGE -> {
                val p = tab.pages.getOrNull(tab.currentPage) ?: tab.pages.firstOrNull()
                val ptsW = (p?.widthPts ?: 612f).coerceAtLeast(1f) * spreadFactor
                val ptsH = (p?.heightPts ?: 792f).coerceAtLeast(1f)
                minOf(availableWidthDp / ptsW, availableHeightDp / ptsH).coerceIn(0.35f, 3.5f)
            }
            ZoomMode.CUSTOM -> tab.zoom
        }

        val widestRowDp = remember(tab.pages, effectiveZoom, useSpread, leftInsetDp) {
            val maxPageW = (tab.pages.maxOfOrNull { it.widthPts } ?: 612f) * effectiveZoom * spreadFactor
            maxPageW + leftInsetDp + 64f
        }
        val listWidthDp = max(maxWidth.value, widestRowDp).dp

        val hitsByPage = remember(store.searchHits.toList(), store.activeHitIndex) {
            store.searchHits.withIndex().groupBy { it.value.pageIndex }
        }

        Box(modifier = Modifier.fillMaxSize().horizontalScroll(hScrollState)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(listWidthDp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = topChromePaddingDp.dp,
                    bottom = 64.dp,
                    start = (leftInsetDp + 24).dp,
                    end = 24.dp,
                ),
            ) {
                items(
                    items = rowIndices,
                    key = { pair -> "${tab.docId}_${useSpread}_${pair.joinToString("_")}" },
                ) { pair ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            pair.forEach { pageIndex ->
                                val pageSize = tab.pages.getOrNull(pageIndex) ?: return@forEach
                                PdfPageBox(
                                    store = store,
                                    tab = tab,
                                    pageIndex = pageIndex,
                                    pageSize = pageSize,
                                    effectiveZoom = effectiveZoom,
                                    colorFilter = colorFilter,
                                    pageHits = hitsByPage[pageIndex].orEmpty(),
                                    onHoverLink = { hoveredLink = it },
                                    listState = listState,
                                    hScrollState = hScrollState,
                                    uiScope = uiScope,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }

        // Interactive vertical page scrubber on right edge
        if (tab.pages.size > 1) {
            val activePageForTrack = scrubbingPage ?: tab.currentPage
            val fraction = (activePageForTrack.toFloat() / (tab.pages.size - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            val trackHeightDp = 180f
            val thumbHeightDp = 28f

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = (topChromePaddingDp + 12).dp, bottom = 72.dp, end = 6.dp)
                    .width(22.dp)
                    .height(trackHeightDp.dp)
                    .pointerInput(tab.docId, tab.pages.size, useSpread) {
                        detectTapGestures { offset ->
                            val frac = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            val target = (frac * (tab.pages.size - 1)).roundToInt()
                            store.jumpToPage(target)
                        }
                    }
                    .pointerInput(tab.docId, tab.pages.size, useSpread) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val frac = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                val target = (frac * (tab.pages.size - 1)).roundToInt()
                                scrubbingPage = target
                                store.setCurrentPage(target)
                                uiScope.launch {
                                    listState.scrollToItem(if (useSpread) target / 2 else target)
                                }
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                val frac = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                val target = (frac * (tab.pages.size - 1)).roundToInt()
                                scrubbingPage = target
                                store.setCurrentPage(target)
                                uiScope.launch {
                                    listState.scrollToItem(if (useSpread) target / 2 else target)
                                }
                            },
                            onDragEnd = { scrubbingPage = null },
                            onDragCancel = { scrubbingPage = null },
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(trackHeightDp.dp)
                        .background(
                            if (store.isDarkUi) Color(0x18FFFFFF) else Color(0x14000000),
                            RoundedCornerShape(999.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = ((trackHeightDp - thumbHeightDp) * fraction).dp)
                            .width(3.dp)
                            .height(thumbHeightDp.dp)
                            .background(
                                if (store.isDarkUi) Color(0x88EDEDEF) else Color(0x8818191C),
                                RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }

            scrubbingPage?.let { sp ->
                Text(
                    text = "Page ${sp + 1} / ${tab.pages.size}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEDEDEF),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = (topChromePaddingDp + 12).dp, bottom = 72.dp, end = 34.dp)
                        .background(Color(0xDE141518), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        hoveredLink?.let { hl ->
            val targetLabel = hl.uri ?: hl.destPage?.let { "Jump to page ${it + 1}" }
            if (targetLabel != null) {
                Text(
                    text = targetLabel,
                    fontSize = 11.sp,
                    color = Color(0xFFEDEDEF),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (leftInsetDp + 20).dp, bottom = 18.dp)
                        .background(Color(0xD8141518), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PdfPageBox(
    store: VnylStore,
    tab: PdfTabState,
    pageIndex: Int,
    pageSize: PageSizeInfo,
    effectiveZoom: Float,
    colorFilter: ColorFilter?,
    pageHits: List<IndexedValue<SearchHitInfo>>,
    onHoverLink: (PageLinkInfo?) -> Unit,
    listState: LazyListState,
    hScrollState: androidx.compose.foundation.ScrollState,
    uiScope: CoroutineScope,
) {
    val density = LocalDensity.current
    val pageDpW = (pageSize.widthPts * effectiveZoom).coerceAtLeast(120f)
    val pageDpH = (pageSize.heightPts * effectiveZoom).coerceAtLeast(160f)

    val pxW = with(density) { pageDpW.dp.toPx().roundToInt().coerceIn(64, 3200) }
    val pxH = with(density) { pageDpH.dp.toPx().roundToInt().coerceIn(64, 4200) }
    val rev = store.pageRevisions[tab.docId to pageIndex] ?: 0

    val key = PageRenderKey(tab.docId, pageIndex, pxW, rev)
    val skiaImage = store.pageImages[key]
        ?: store.pageImages.entries
            .filter { it.key.docId == tab.docId && it.key.pageIndex == pageIndex }
            .maxByOrNull { it.key.revision }
            ?.value

    LaunchedEffect(key) {
        store.requestPageRender(tab.docId, pageIndex, pxW, pxH)
        store.ensureTextSpans(tab.docId, pageIndex)
        store.ensurePageLinks(tab.docId, pageIndex)
        store.ensureFormFields(tab.docId, pageIndex)
    }

    val pageBg = when (store.pdfColorMode) {
        PdfColorMode.LIGHT -> Color.White
        PdfColorMode.DARK -> Color(0xFF14171D)
        PdfColorMode.SEPIA -> Color(0xFFF5ECD8)
    }

    var dragStartNorm by remember { mutableStateOf<Offset?>(null) }
    var dragEndNorm by remember { mutableStateOf<Offset?>(null) }
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hoveredLinkLocal by remember { mutableStateOf<PageLinkInfo?>(null) }
    val liveInkNorm = remember { mutableStateListOf<Offset>() }
    val links = store.pageLinksCache[tab.docId to pageIndex].orEmpty()
    val formFields = store.formFieldsCache[tab.docId to pageIndex].orEmpty()
    val pageAnnotations = remember(store.annotationsList.toList(), pageIndex) {
        store.annotationsList.filter { it.pageIndex == pageIndex && it.kind != "Widget" && it.kind != "Link" }
    }
    val selectedAnnot = store.selectedAnnotation?.takeIf { it.pageIndex == pageIndex }

    Box(
        modifier = Modifier
            .width(pageDpW.dp)
            .height(pageDpH.dp)
            .onGloballyPositioned { pageCoords = it }
            .shadow(8.dp, RoundedCornerShape(3.dp))
            .background(pageBg, RoundedCornerShape(3.dp))
            .border(
                width = 0.75.dp,
                color = if (store.pdfColorMode == PdfColorMode.DARK) Color(0x22FFFFFF) else Color(0x16000000),
                shape = RoundedCornerShape(3.dp),
            )
            .onPointerEvent(PointerEventType.Move) { event ->
                val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                val wPx = with(density) { pageDpW.dp.toPx() }.coerceAtLeast(1f)
                val hPx = with(density) { pageDpH.dp.toPx() }.coerceAtLeast(1f)
                val nx = (pos.x / wPx).coerceIn(0f, 1f)
                val ny = (pos.y / hPx).coerceIn(0f, 1f)
                val found = links.firstOrNull { l -> nx in l.left..l.right && ny in l.top..l.bottom }
                hoveredLinkLocal = found
                onHoverLink(found)
            }
            .onPointerEvent(PointerEventType.Exit) {
                hoveredLinkLocal = null
                onHoverLink(null)
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                val pos = event.changes.firstOrNull()?.position ?: Offset.Zero
                if (event.button == PointerButton.Secondary) {
                    val wPx = with(density) { pageDpW.dp.toPx() }.coerceAtLeast(1f)
                    val hPx = with(density) { pageDpH.dp.toPx() }.coerceAtLeast(1f)
                    val norm = Offset(
                        (pos.x / wPx).coerceIn(0f, 1f),
                        (pos.y / hPx).coerceIn(0f, 1f),
                    )
                    val rootPos = pageCoords?.localToRoot(pos) ?: pos
                    store.contextMenu = ContextMenuState(
                        offset = DpOffset(
                            with(density) { rootPos.x.toDp() },
                            with(density) { rootPos.y.toDp() },
                        ),
                        pageIndex = pageIndex,
                        normPos = norm,
                    )
                } else {
                    store.contextMenu = null
                    store.openMenuId = null
                    store.appearanceOpen = false
                }
            }
            .pointerInput(tab.docId, pageIndex, store.activeTool, links, formFields, pageAnnotations) {
                detectTapGestures { offset ->
                    val nx = (offset.x / size.width.toFloat()).coerceIn(0.01f, 0.99f)
                    val ny = (offset.y / size.height.toFloat()).coerceIn(0.01f, 0.99f)
                    when (store.activeTool) {
                        ToolMode.NOTE -> {
                            store.pendingNote = PendingNoteRequest(tab.docId, pageIndex, nx, ny)
                        }
                        ToolMode.STAMP -> {
                            store.pendingStamp?.let { stamp ->
                                store.commitImageStamp(tab.docId, pageIndex, nx, ny, stamp)
                            }
                        }
                        ToolMode.SELECT -> {
                            val hitField = formFields.firstOrNull { f ->
                                nx in f.left..f.right && ny in f.top..f.bottom
                            }
                            if (hitField != null) {
                                store.selectedAnnotation = null
                                if (hitField.kind == "Checkbox" || hitField.kind == "RadioButton") {
                                    store.updateFormField(tab.docId, hitField, "", !hitField.checked)
                                } else if (hitField.kind == "Text") {
                                    store.pendingFormEdit = PendingFormEdit(tab.docId, hitField)
                                }
                                return@detectTapGestures
                            }
                            val hitLink = links.firstOrNull { l ->
                                nx in l.left..l.right && ny in l.top..l.bottom
                            }
                            if (hitLink != null) {
                                store.selectedAnnotation = null
                                if (hitLink.destPage != null) {
                                    store.jumpToPage(hitLink.destPage)
                                } else if (!hitLink.uri.isNullOrBlank()) {
                                    runCatching {
                                        val u = URI(hitLink.uri)
                                        if (u.scheme == "http" || u.scheme == "https") {
                                            Desktop.getDesktop().browse(u)
                                        }
                                    }
                                }
                                return@detectTapGestures
                            }
                            val hitAnnot = pageAnnotations.firstOrNull { a ->
                                nx in (a.left - 0.01f)..(a.right + 0.01f) &&
                                    ny in (a.top - 0.01f)..(a.bottom + 0.01f)
                            }
                            store.selectedAnnotation = if (hitAnnot != null && hitAnnot != store.selectedAnnotation) {
                                hitAnnot
                            } else {
                                null
                            }
                            if (hitAnnot == null) {
                                store.selection = null
                            }
                        }
                        else -> Unit
                    }
                }
            }
            .pointerInput(tab.docId, pageIndex, store.activeTool, store.activeSwatchIndex) {
                detectDragGestures(
                    onDragStart = { start ->
                        val nx = (start.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val ny = (start.y / size.height.toFloat()).coerceIn(0f, 1f)
                        store.selectedAnnotation = null
                        when (store.activeTool) {
                            ToolMode.SELECT, ToolMode.HIGHLIGHT -> {
                                dragStartNorm = Offset(nx, ny)
                                dragEndNorm = Offset(nx, ny)
                            }
                            ToolMode.INK -> {
                                liveInkNorm.clear()
                                liveInkNorm.add(Offset(nx, ny))
                            }
                            else -> Unit
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val pos = change.position
                        val nx = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val ny = (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                        when (store.activeTool) {
                            ToolMode.PAN -> {
                                uiScope.launch {
                                    listState.scrollBy(-dragAmount.y)
                                    hScrollState.scrollBy(-dragAmount.x)
                                }
                            }
                            ToolMode.SELECT, ToolMode.HIGHLIGHT -> {
                                dragEndNorm = Offset(nx, ny)
                                val s = dragStartNorm ?: Offset(nx, ny)
                                val spans = store.textSpansCache[tab.docId to pageIndex].orEmpty()
                                val picked = pickSpansInBox(spans, s, Offset(nx, ny))
                                if (picked.isNotEmpty()) {
                                    store.selection = TextSelectionState(
                                        docId = tab.docId,
                                        pageIndex = pageIndex,
                                        rects = picked.map { NormRect(it.left, it.top, it.right, it.bottom) },
                                        text = picked.joinToString(" ") { it.text.trim() },
                                    )
                                }
                            }
                            ToolMode.INK -> liveInkNorm.add(Offset(nx, ny))
                            else -> Unit
                        }
                    },
                    onDragEnd = {
                        when (store.activeTool) {
                            ToolMode.HIGHLIGHT -> {
                                val s = dragStartNorm
                                val e = dragEndNorm
                                val sel = store.selection
                                if (sel != null && sel.pageIndex == pageIndex && sel.rects.isNotEmpty()) {
                                    store.commitHighlight(tab.docId, pageIndex, sel.rects)
                                } else if (s != null && e != null && abs(e.x - s.x) > 0.01f) {
                                    val box = NormRect(
                                        min(s.x, e.x),
                                        min(s.y, e.y),
                                        max(s.x, e.x),
                                        max(s.y, e.y).coerceAtLeast(min(s.y, e.y) + 0.015f),
                                    )
                                    store.commitHighlight(tab.docId, pageIndex, listOf(box))
                                }
                                store.selection = null
                            }
                            ToolMode.INK -> {
                                if (liveInkNorm.size >= 2) {
                                    store.commitInkStroke(tab.docId, pageIndex, liveInkNorm.toList())
                                }
                                liveInkNorm.clear()
                            }
                            else -> Unit
                        }
                        dragStartNorm = null
                        dragEndNorm = null
                    },
                    onDragCancel = {
                        dragStartNorm = null
                        dragEndNorm = null
                        liveInkNorm.clear()
                    },
                )
            },
    ) {
        val sel = store.selection?.takeIf { it.docId == tab.docId && it.pageIndex == pageIndex }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val skCanvas = drawContext.canvas.nativeCanvas
            if (skiaImage != null) {
                val src = Rect.makeWH(skiaImage.width.toFloat(), skiaImage.height.toFloat())
                val dst = Rect.makeWH(size.width, size.height)
                val paint = Paint().apply {
                    isAntiAlias = true
                    this.colorFilter = colorFilter
                }
                skCanvas.drawImageRect(skiaImage, src, dst, paint)
            }

            if (formFields.isNotEmpty()) {
                val formPaint = Paint().apply {
                    isAntiAlias = true
                    mode = PaintMode.STROKE
                    strokeWidth = 1.1f
                    color = org.jetbrains.skia.Color.makeARGB(95, 90, 102, 118)
                }
                for (f in formFields) {
                    skCanvas.drawRect(
                        Rect.makeLTRB(
                            f.left * size.width,
                            f.top * size.height,
                            f.right * size.width,
                            f.bottom * size.height,
                        ),
                        formPaint,
                    )
                }
            }

            hoveredLinkLocal?.let { hl ->
                val lp = Paint().apply {
                    color = org.jetbrains.skia.Color.makeARGB(170, 82, 92, 106)
                    strokeWidth = 1.4f
                }
                val y = hl.bottom * size.height
                skCanvas.drawLine(hl.left * size.width, y, hl.right * size.width, y, lp)
            }

            selectedAnnot?.let { a ->
                val ap = Paint().apply {
                    isAntiAlias = true
                    mode = PaintMode.STROKE
                    strokeWidth = 1.8f
                    color = org.jetbrains.skia.Color.makeARGB(220, 24, 25, 28)
                }
                skCanvas.drawRect(
                    Rect.makeLTRB(
                        a.left * size.width - 3f,
                        a.top * size.height - 3f,
                        a.right * size.width + 3f,
                        a.bottom * size.height + 3f,
                    ),
                    ap,
                )
            }

            if (sel != null) {
                val selPaint = Paint().apply {
                    color = org.jetbrains.skia.Color.makeARGB(78, 100, 114, 132)
                }
                for (r in sel.rects) {
                    skCanvas.drawRect(
                        Rect.makeLTRB(
                            r.left * size.width,
                            r.top * size.height,
                            r.right * size.width,
                            r.bottom * size.height,
                        ),
                        selPaint,
                    )
                }
            } else if (store.activeTool == ToolMode.HIGHLIGHT && dragStartNorm != null && dragEndNorm != null) {
                val s = dragStartNorm!!
                val e = dragEndNorm!!
                skCanvas.drawRect(
                    Rect.makeLTRB(
                        min(s.x, e.x) * size.width,
                        min(s.y, e.y) * size.height,
                        max(s.x, e.x) * size.width,
                        max(s.y, e.y) * size.height,
                    ),
                    Paint().apply { color = store.activeSwatch.argb },
                )
            }

            if (liveInkNorm.size >= 2) {
                val inkPath = Path()
                inkPath.moveTo(liveInkNorm[0].x * size.width, liveInkNorm[0].y * size.height)
                for (i in 1 until liveInkNorm.size) {
                    inkPath.lineTo(liveInkNorm[i].x * size.width, liveInkNorm[i].y * size.height)
                }
                val inkPaint = Paint().apply {
                    isAntiAlias = true
                    mode = PaintMode.STROKE
                    strokeWidth = 2.5f * effectiveZoom
                    strokeCap = PaintStrokeCap.ROUND
                    strokeJoin = PaintStrokeJoin.ROUND
                    color = (store.activeSwatch.argb and 0x00FFFFFF) or (0xFF shl 24)
                }
                skCanvas.drawPath(inkPath, inkPaint)
            }

            if (pageHits.isNotEmpty()) {
                for ((globalIdx, hit) in pageHits) {
                    val isActive = globalIdx == store.activeHitIndex
                    val paint = Paint().apply {
                        color = if (isActive) {
                            org.jetbrains.skia.Color.makeARGB(135, 255, 149, 0)
                        } else {
                            org.jetbrains.skia.Color.makeARGB(90, 255, 230, 64)
                        }
                    }
                    skCanvas.drawRect(
                        Rect.makeLTRB(
                            hit.left * size.width,
                            hit.top * size.height,
                            hit.right * size.width,
                            hit.bottom * size.height,
                        ),
                        paint,
                    )
                }
            }
        }

        // Inline glass delete pill for selected annotation
        selectedAnnot?.let { annot ->
            val pillX = (annot.left * pageDpW).coerceIn(8f, (pageDpW - 150f).coerceAtLeast(8f))
            val pillY = (annot.top * pageDpH - 34f).coerceAtLeast(6f)
            Row(
                modifier = Modifier
                    .offset(x = pillX.dp, y = pillY.dp)
                    .shadow(6.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xEA16171A))
                    .border(0.75.dp, Color(0x36FFFFFF), RoundedCornerShape(999.dp))
                    .clickable {
                        store.deleteAnnotationItem(annot)
                        store.selectedAnnotation = null
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("${annot.kind}", fontSize = 11.sp, color = Color(0xFFEDEDEF))
                Icon(Icons.Rounded.Delete, contentDescription = "Delete annotation", tint = Color(0xFFE56B6F), modifier = Modifier.size(13.dp))
            }
        }

        if (skiaImage == null) {
            Text(
                text = "Rendering page ${pageIndex + 1}…",
                fontSize = 12.sp,
                color = if (store.pdfColorMode == PdfColorMode.DARK) Color(0xFF9498A1) else Color(0xFF6B707B),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun pickSpansInBox(
    spans: List<TextSpanInfo>,
    a: Offset,
    b: Offset,
): List<TextSpanInfo> {
    if (spans.isEmpty()) return emptyList()
    val minX = min(a.x, b.x)
    val maxX = max(a.x, b.x)
    val minY = min(a.y, b.y)
    val maxY = max(a.y, b.y)
    return spans.filter { s ->
        s.right >= minX && s.left <= maxX && s.bottom >= minY && s.top <= maxY
    }
}
