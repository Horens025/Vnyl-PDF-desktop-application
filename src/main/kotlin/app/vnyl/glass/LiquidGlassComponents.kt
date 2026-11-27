package app.vnyl.glass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vnyl.state.GlassLook
import app.vnyl.state.MotionPreset
import app.vnyl.state.UiStyle
import org.jetbrains.skia.Canvas as SkiaCanvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import kotlin.math.abs

/** Global root pointer coordinate so every Liquid Glass lens reflects cursor glint. */
val LocalWindowPointer = compositionLocalOf { Offset(-2000f, -2000f) }

fun <T> motionSpring(preset: MotionPreset): AnimationSpec<T> = when (preset) {
    MotionPreset.BOUNCY -> SpringSpec(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow,
    )
    MotionPreset.SNAPPY -> SpringSpec(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )
    MotionPreset.REDUCED -> snap()
}

data class GlassPalette(
    val bg: Color,
    val bgStrong: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentContent: Color,
    val danger: Color,
    val hover: Color,
    val activeFill: Color,
    val tintRgba: FloatArray,
    val blurDp: Float,
    val refractStrength: Float,
    val highlightAlpha: Float,
)

fun resolveGlassPalette(
    isDark: Boolean,
    style: UiStyle,
    glassLook: GlassLook,
    refractionScale: Float,
): GlassPalette {
    val isClear = glassLook == GlassLook.CLEAR
    return if (isDark) {
        GlassPalette(
            bg = if (style == UiStyle.CLASSIC) Color(0xFF13151A) else Color(0xCC111318),
            bgStrong = if (style == UiStyle.CLASSIC) Color(0xFF181B21) else Color(0xEB101217),
            border = if (style == UiStyle.CLASSIC) Color(0xFF2B2E36) else Color(0x2EFFFFFF),
            text = Color(0xFFF5F6FA),
            textMuted = Color(0xFFB4BAC8),
            accent = Color(0xFF5A96FF),
            accentContent = Color(0xFFFFFFFF),
            danger = Color(0xFFFF6B70),
            hover = Color(0x16FFFFFF),
            activeFill = Color(0x2E4F8EF7),
            tintRgba = if (isClear) floatArrayOf(0.055f, 0.060f, 0.075f, 0.34f) else floatArrayOf(0.055f, 0.060f, 0.075f, 0.68f),
            blurDp = if (isClear) 10f else 22f,
            refractStrength = (if (isClear) 1.25f else 0.95f) * refractionScale,
            highlightAlpha = if (isClear) 1.06f else 0.88f,
        )
    } else {
        GlassPalette(
            bg = if (style == UiStyle.CLASSIC) Color(0xFFFFFFFF) else Color(0xC8FFFFFF),
            bgStrong = if (style == UiStyle.CLASSIC) Color(0xFFFFFFFF) else Color(0xEEFFFFFF),
            border = if (style == UiStyle.CLASSIC) Color(0xFFDADCE2) else Color(0xB8FFFFFF),
            text = Color(0xFF111318),
            textMuted = Color(0xFF565C68),
            accent = Color(0xFF2563EB),
            accentContent = Color(0xFFFFFFFF),
            danger = Color(0xFFD63B3B),
            hover = Color(0x0E111318),
            activeFill = Color(0x1C2563EB),
            tintRgba = if (isClear) floatArrayOf(0.985f, 0.987f, 0.992f, 0.26f) else floatArrayOf(0.980f, 0.982f, 0.988f, 0.64f),
            blurDp = if (isClear) 10f else 22f,
            refractStrength = (if (isClear) 1.20f else 0.95f) * refractionScale,
            highlightAlpha = if (isClear) 1.05f else 0.92f,
        )
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    isDark: Boolean,
    style: UiStyle,
    glassLook: GlassLook,
    refractionScale: Float = 1.0f,
    strong: Boolean = false,
    elevation: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = resolveGlassPalette(isDark, style, glassLook, refractionScale)
    val shape = RoundedCornerShape(cornerRadius)
    val rootPointer = LocalWindowPointer.current
    var rootPos by remember { mutableStateOf(Offset.Zero) }

    if (style == UiStyle.CLASSIC) {
        Box(
            modifier = modifier
                .shadow(4.dp, shape, clip = false)
                .clip(shape)
                .background(if (strong) palette.bgStrong else palette.bg)
                .border(1.dp, palette.border, shape),
            content = content,
        )
        return
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                rootPos = coords.positionInRoot()
            }
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .drawWithContent {
                val w = size.width
                val h = size.height
                val rPx = cornerRadius.toPx().coerceAtMost(minOf(w, h) * 0.5f)
                val blurPx = palette.blurDp * density * 0.6f
                val tint = palette.tintRgba
                val tintAlpha = if (strong) (tint[3] + 0.20f).coerceAtMost(0.88f) else tint[3]

                val localPtr = rootPointer - rootPos
                val pointerActive = if (rootPointer.x > -500f && rootPointer.y > -500f) 1f else 0f

                drawIntoCanvas { canvas ->
                    val skCanvas = canvas.nativeCanvas
                    val bounds = Rect.makeWH(w, h)
                    val rrect = RRect.makeXYWH(0f, 0f, w, h, rPx)
                    val backdropFilter = LiquidGlassShaders.createGlassBackdropFilter(
                        widthPx = w,
                        heightPx = h,
                        cornerRadiusPx = rPx,
                        blurSigma = blurPx,
                        refractStrength = palette.refractStrength,
                        chromaticAberration = 1.0f,
                        tintR = tint[0],
                        tintG = tint[1],
                        tintB = tint[2],
                        tintA = tintAlpha,
                        highlightAlpha = palette.highlightAlpha,
                        isDark = isDark,
                        pointerLocalX = localPtr.x,
                        pointerLocalY = localPtr.y,
                        pointerActive = pointerActive,
                    )
                    skCanvas.save()
                    skCanvas.clipRRect(rrect, true)
                    skCanvas.saveLayer(SkiaCanvas.SaveLayerRec(bounds, null, backdropFilter))
                    skCanvas.restore()
                    skCanvas.restore()
                }
                drawContent()
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.border,
                        palette.border.copy(alpha = palette.border.alpha * 0.35f),
                    ),
                ),
                shape = shape,
            ),
        content = content,
    )
}

/** Tactile spring-animated Liquid Glass pill button with hover and press scale. */
@Composable
fun GlassActionPill(
    onClick: () -> Unit,
    palette: GlassPalette,
    motionPreset: MotionPreset,
    active: Boolean = false,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else if (hovered && enabled) 1.02f else 1.0f,
        animationSpec = motionSpring(motionPreset),
        label = "pill_scale",
    )

    val bg = when {
        active -> palette.activeFill
        hovered && enabled -> palette.hover
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> palette.textMuted.copy(alpha = 0.4f)
        active -> palette.accent
        else -> palette.text
    }

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg)
            .border(
                width = 0.75.dp,
                color = if (active) palette.accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(cornerRadius),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

@Composable
fun <T> MorphingSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    isDark: Boolean,
    motionPreset: MotionPreset,
    accent: Color = Color(0xFF0A84FF),
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = motionSpring(motionPreset),
        label = "morph_segment_idx",
    )
    val velocityNorm = abs(selectedIndex - animatedIndex).coerceIn(0f, 1f)

    var timeSec by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                timeSec = ((now - start) / 1_000_000_000.0).toFloat()
            }
        }
    }

    val trackShape = RoundedCornerShape(999.dp)
    val trackBg = if (isDark) Color(0x52090A0D) else Color(0x12000000)
    val trackBorder = if (isDark) Color(0x22FFFFFF) else Color(0x16000000)
    val textActive = if (isDark) Color.White else Color(0xFF111318)
    val textMuted = if (isDark) Color(0xFFA7AEBE) else Color(0xFF565C68)

    BoxWithConstraints(
        modifier = modifier
            .height(34.dp)
            .clip(trackShape)
            .background(trackBg)
            .border(0.75.dp, trackBorder, trackShape)
            .padding(3.dp),
    ) {
        val count = items.size.coerceAtLeast(1)
        val slotWidth = maxWidth / count
        val stretchDp = (velocityNorm * 10f).dp
        val pillOffset by animateDpAsState(
            targetValue = slotWidth * animatedIndex - (stretchDp * 0.5f),
            animationSpec = motionSpring(motionPreset),
            label = "morph_pill_offset",
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset.coerceAtLeast(0.dp))
                .width((slotWidth + stretchDp).coerceAtMost(maxWidth))
                .fillMaxHeight()
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val builder = RuntimeShaderBuilder(LiquidGlassShaders.morphPillEffect).apply {
                            uniform("size", size.width.coerceAtLeast(2f), size.height.coerceAtLeast(2f))
                            uniform("cornerRadius", size.height * 0.5f)
                            if (isDark) {
                                uniform("baseColor", 0.16f, 0.28f, 0.48f, 0.94f)
                                uniform("glowColor", accent.red, accent.green, accent.blue, 0.96f)
                            } else {
                                uniform("baseColor", 1.0f, 1.0f, 1.0f, 0.98f)
                                uniform("glowColor", accent.red, accent.green, accent.blue, 0.22f)
                            }
                            uniform("velocityNorm", velocityNorm)
                            uniform("timeSec", timeSec)
                        }
                        val shader: Shader = builder.makeShader()
                        val paint = Paint().apply { this.shader = shader }
                        canvas.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), paint)
                    }
                },
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { (value, label) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(trackShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) textActive else textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
