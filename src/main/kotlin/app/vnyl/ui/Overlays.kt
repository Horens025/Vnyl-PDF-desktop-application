package app.vnyl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vnyl.glass.GlassPalette
import app.vnyl.glass.LiquidGlassSurface
import app.vnyl.glass.MorphingSegmentedControl
import app.vnyl.state.DirtyConfirmAction
import app.vnyl.state.GlassLook
import app.vnyl.state.MotionPreset
import app.vnyl.state.PdfColorMode
import app.vnyl.state.ToolMode
import app.vnyl.state.UiStyle
import app.vnyl.state.UiTheme
import app.vnyl.state.VnylStore
import app.vnyl.state.ZoomMode
import kotlin.math.roundToInt

data class AppCommand(
    val id: String,
    val title: String,
    val shortcut: String,
    val requiresDoc: Boolean = false,
    val run: () -> Unit,
)

@Composable
fun SearchOverlay(store: VnylStore, palette: GlassPalette) {
    LiquidGlassSurface(
        modifier = Modifier.width(350.dp),
        cornerRadius = 18.dp,
        isDark = store.isDarkUi,
        style = store.uiStyle,
        glassLook = store.glassLook,
        refractionScale = store.refractionStrength,
        strong = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = store.searchQuery,
                onValueChange = { store.runSearch(it) },
                singleLine = true,
                textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.hover)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                decorationBox = { inner ->
                    if (store.searchQuery.isEmpty()) {
                        Text("Find in document…", color = palette.textMuted, fontSize = 13.sp)
                    }
                    inner()
                },
            )
            if (store.searchHits.isNotEmpty()) {
                Text("${store.activeHitIndex + 1}/${store.searchHits.size}", fontSize = 11.sp, color = palette.textMuted)
            }
            SmallOverlayIconButton(onClick = { store.nextSearchHit(-1) }, enabled = store.searchHits.isNotEmpty(), tint = palette.text) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous", modifier = Modifier.size(16.dp))
            }
            SmallOverlayIconButton(onClick = { store.nextSearchHit(1) }, enabled = store.searchHits.isNotEmpty(), tint = palette.text) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", modifier = Modifier.size(16.dp))
            }
            SmallOverlayIconButton(
                onClick = {
                    store.searchOpen = false
                    store.clearSearch()
                },
                tint = palette.textMuted,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AppearancePopover(store: VnylStore, palette: GlassPalette) {
    LiquidGlassSurface(
        modifier = Modifier.width(340.dp),
        cornerRadius = 22.dp,
        isDark = store.isDarkUi,
        style = store.uiStyle,
        glassLook = store.glassLook,
        refractionScale = store.refractionStrength,
        strong = true,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Appearance & Shaders", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                SmallOverlayIconButton(onClick = { store.appearanceOpen = false }, tint = palette.textMuted) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }

            OverlaySectionLabel("Theme", palette)
            MorphingSegmentedControl(
                items = listOf(UiTheme.LIGHT to "Light", UiTheme.DARK to "Dark"),
                selected = if (store.isDarkUi) UiTheme.DARK else UiTheme.LIGHT,
                onSelect = {
                    store.uiTheme = it
                    store.persistPrefs()
                },
                isDark = store.isDarkUi,
                motionPreset = store.motionPreset,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth(),
            )

            OverlaySectionLabel("Interface Style", palette)
            MorphingSegmentedControl(
                items = listOf(UiStyle.LIQUID_GLASS to "Liquid Glass", UiStyle.CLASSIC to "Classic"),
                selected = store.uiStyle,
                onSelect = {
                    store.uiStyle = it
                    store.persistPrefs()
                },
                isDark = store.isDarkUi,
                motionPreset = store.motionPreset,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth(),
            )

            OverlaySectionLabel("Glass Finish", palette)
            MorphingSegmentedControl(
                items = listOf(GlassLook.FROSTED to "Frosted", GlassLook.CLEAR to "Clear"),
                selected = store.glassLook,
                onSelect = {
                    store.glassLook = it
                    store.persistPrefs()
                },
                isDark = store.isDarkUi,
                motionPreset = store.motionPreset,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth(),
            )

            OverlaySectionLabel("PDF Reading Filter (GPU)", palette)
            MorphingSegmentedControl(
                items = listOf(
                    PdfColorMode.LIGHT to "Paper",
                    PdfColorMode.DARK to "Night",
                    PdfColorMode.SEPIA to "Sepia",
                ),
                selected = store.pdfColorMode,
                onSelect = {
                    store.pdfColorMode = it
                    store.persistPrefs()
                },
                isDark = store.isDarkUi,
                motionPreset = store.motionPreset,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth(),
            )

            OverlaySectionLabel("Spring Physics", palette)
            MorphingSegmentedControl(
                items = listOf(
                    MotionPreset.BOUNCY to "Bouncy",
                    MotionPreset.SNAPPY to "Snappy",
                    MotionPreset.REDUCED to "Reduced",
                ),
                selected = store.motionPreset,
                onSelect = {
                    store.motionPreset = it
                    store.persistPrefs()
                },
                isDark = store.isDarkUi,
                motionPreset = store.motionPreset,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OverlaySectionLabel("Refraction & Chromatic Bevel", palette)
                    Text("${(store.refractionStrength * 100).roundToInt()}%", fontSize = 11.sp, color = palette.textMuted)
                }
                Slider(
                    value = store.refractionStrength,
                    onValueChange = { store.refractionStrength = it },
                    onValueChangeFinished = { store.persistPrefs() },
                    valueRange = 0f..1.8f,
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                )
            }
        }
    }
}

@Composable
fun CommandPaletteModal(
    store: VnylStore,
    commands: List<AppCommand>,
    palette: GlassPalette,
) {
    var query by remember { mutableStateOf("") }
    val hasDoc = store.activeTab != null
    val filtered = remember(query, hasDoc, commands) {
        commands.filter { cmd ->
            (!cmd.requiresDoc || hasDoc) &&
                (query.isBlank() || cmd.title.contains(query, ignoreCase = true) || cmd.shortcut.contains(query, ignoreCase = true))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { store.commandPaletteOpen = false },
        contentAlignment = Alignment.TopCenter,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .padding(top = 96.dp)
                .width(500.dp)
                .clickable(enabled = false) {},
            cornerRadius = 22.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.hover)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Type a command (Open, Save, Export, Rotate, Split…)", color = palette.textMuted, fontSize = 13.sp)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    store.commandPaletteOpen = false
                                    cmd.run()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cmd.title, fontSize = 13.sp, color = palette.text)
                            if (cmd.shortcut.isNotBlank()) {
                                Text(
                                    text = cmd.shortcut,
                                    fontSize = 11.sp,
                                    color = palette.textMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.hover)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutsModal(store: VnylStore, palette: GlassPalette) {
    val rows = listOf(
        "Open PDF" to "Ctrl + O",
        "Save / Save As" to "Ctrl + S / Ctrl + Shift + S",
        "Undo / Redo" to "Ctrl + Z / Ctrl + Y",
        "Copy Selected Text" to "Ctrl + C",
        "Find in Document" to "Ctrl + F",
        "Toggle Sidebar" to "Ctrl + B",
        "Toggle Split View" to "Ctrl + \\",
        "Command Palette" to "Ctrl + Shift + K",
        "Reading Mode (Focus)" to "F11",
        "Zoom In / Out / Fit Width" to "Ctrl + = / - / 0",
        "Close Active Tab" to "Ctrl + W",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { store.shortcutsOpen = false },
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
                    Text("Keyboard Shortcuts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    SmallOverlayIconButton(onClick = { store.shortcutsOpen = false }, tint = palette.textMuted) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = palette.border)
                rows.forEach { (label, keys) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 12.5.sp, color = palette.text)
                        Text(keys, fontSize = 11.5.sp, color = palette.textMuted, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteComposerModal(store: VnylStore, palette: GlassPalette) {
    val req = store.pendingNote ?: return
    var noteText by remember(req) { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .clickable { store.pendingNote = null },
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
                Text("Add PDF Text Note (Page ${req.pageIndex + 1})", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                BasicTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.hover)
                        .padding(10.dp),
                    decorationBox = { inner ->
                        if (noteText.isEmpty()) Text("Type note text to bake into page…", color = palette.textMuted, fontSize = 12.5.sp)
                        inner()
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { store.pendingNote = null },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("Cancel", fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            store.commitTextNote(req.docId, req.pageIndex, req.normX, req.normY, noteText)
                            store.pendingNote = null
                        },
                        enabled = noteText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.accentContent,
                        ),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("Place Note", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun GlassContextMenu(
    store: VnylStore,
    palette: GlassPalette,
    onExportPng: () -> Unit,
) {
    val ctx = store.contextMenu ?: return
    val tab = store.activeTab ?: return
    val hasSel = store.selection != null && store.selection!!.text.isNotBlank()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable { store.contextMenu = null },
    ) {
        val menuWidth = 232.dp
        val menuHeight = 224.dp
        val posX = ctx.offset.x.coerceIn(12.dp, (maxWidth - menuWidth - 12.dp).coerceAtLeast(12.dp))
        val posY = ctx.offset.y.coerceIn(12.dp, (maxHeight - menuHeight - 12.dp).coerceAtLeast(12.dp))
        LiquidGlassSurface(
            modifier = Modifier
                .offset(x = posX, y = posY)
                .width(menuWidth)
                .clickable(enabled = false) {},
            cornerRadius = 16.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ContextMenuItem("Copy Selected Text", enabled = hasSel, palette = palette) {
                    store.copySelectionToClipboard()
                    store.contextMenu = null
                }
                ContextMenuItem("Highlight Selection", enabled = hasSel, palette = palette) {
                    store.selection?.let { sel ->
                        store.commitHighlight(sel.docId, sel.pageIndex, sel.rects)
                        store.selection = null
                    }
                    store.contextMenu = null
                }
                ContextMenuItem("Add Note Here", enabled = true, palette = palette) {
                    store.pendingNote = app.vnyl.state.PendingNoteRequest(
                        tab.docId,
                        ctx.pageIndex,
                        ctx.normPos.x,
                        ctx.normPos.y,
                    )
                    store.contextMenu = null
                }
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 3.dp))
                ContextMenuItem("Rotate Page 90° CW", enabled = true, palette = palette) {
                    store.jumpToPage(ctx.pageIndex)
                    store.rotateCurrentPage(1)
                    store.contextMenu = null
                }
                ContextMenuItem("Export Page as PNG…", enabled = true, palette = palette) {
                    store.jumpToPage(ctx.pageIndex)
                    store.contextMenu = null
                    onExportPng()
                }
                ContextMenuItem("Delete Page", enabled = tab.pages.size > 1, palette = palette, danger = true) {
                    store.jumpToPage(ctx.pageIndex)
                    store.deleteCurrentPage()
                    store.contextMenu = null
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    label: String,
    enabled: Boolean,
    palette: GlassPalette,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = when {
                !enabled -> palette.textMuted.copy(alpha = 0.4f)
                danger -> palette.danger
                else -> palette.text
            },
        )
    }
}

@Composable
fun OverlaySectionLabel(text: String, palette: GlassPalette) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = palette.textMuted,
    )
}

@Composable
fun SmallOverlayIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) tint else tint.copy(alpha = 0.35f),
        ) {
            content()
        }
    }
}

@Composable
fun PasswordPromptModal(store: VnylStore, palette: GlassPalette) {
    val file = store.pendingPasswordFile ?: return
    var password by remember(file) { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable {
                store.pendingPasswordFile = null
                store.passwordError = null
            },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(380.dp).clickable(enabled = false) {},
            cornerRadius = 20.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Encrypted PDF", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "Enter the password to open ${file.name}.",
                    fontSize = 12.sp,
                    color = palette.textMuted,
                )
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(color = palette.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.hover)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (password.isEmpty()) Text("Document password…", color = palette.textMuted, fontSize = 12.5.sp)
                        inner()
                    },
                )
                store.passwordError?.let { err ->
                    Text(err, fontSize = 11.5.sp, color = palette.danger)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            store.pendingPasswordFile = null
                            store.passwordError = null
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                    ) { Text("Cancel", fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { store.openFile(file, password) },
                        enabled = password.isNotEmpty(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.accentContent),
                    ) { Text("Unlock", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
fun DirtyConfirmModal(
    store: VnylStore,
    palette: GlassPalette,
    onSaveActive: () -> Unit,
) {
    val action = store.pendingDirtyConfirm ?: return
    val targetLabel = when (action) {
        is DirtyConfirmAction.CloseTab -> store.tabs.getOrNull(action.index)?.title ?: "this document"
        is DirtyConfirmAction.ExitApp -> "modified tabs before exiting"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable { store.pendingDirtyConfirm = null },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.width(400.dp).clickable(enabled = false) {},
            cornerRadius = 20.dp,
            isDark = store.isDarkUi,
            style = store.uiStyle,
            glassLook = store.glassLook,
            refractionScale = store.refractionStrength,
            strong = true,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Unsaved Changes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(
                    "Save changes to $targetLabel before closing?",
                    fontSize = 12.5.sp,
                    color = palette.textMuted,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { store.pendingDirtyConfirm = null },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.hover, contentColor = palette.text),
                    ) { Text("Cancel", fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            store.pendingDirtyConfirm = null
                            when (action) {
                                is DirtyConfirmAction.CloseTab -> store.closeTab(action.index)
                                is DirtyConfirmAction.ExitApp -> action.onExit()
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.danger.copy(alpha = 0.16f), contentColor = palette.danger),
                    ) { Text("Don't Save", fontSize = 12.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            store.pendingDirtyConfirm = null
                            onSaveActive()
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.accentContent),
                    ) { Text("Save…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
