package app.vnyl.glass

import app.vnyl.state.PdfColorMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Single-canvas Skia / SkSL GPU shaders for Liquid Glass refraction,
 * chromatic rim dispersion, cursor-reactive specular glint, and morphing capsules.
 */
object LiquidGlassShaders {

    private const val LIQUID_GLASS_SKSL = """
        uniform shader content;
        uniform float2 size;
        uniform float cornerRadius;
        uniform float refractStrength;
        uniform float chromaticAberration;
        uniform float4 tintColor;
        uniform float highlightAlpha;
        uniform float isDark;
        uniform float2 pointerLocal;
        uniform float pointerActive;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = max(size * 0.5, float2(1.0));
            float r = min(cornerRadius, min(halfSize.x, halfSize.y));
            float2 p = fragCoord - halfSize;

            float dist = sdRoundedBox(p, halfSize, r);
            if (dist > 0.5) {
                return half4(0.0);
            }

            float eps = 1.5;
            float dx = sdRoundedBox(p + float2(eps, 0.0), halfSize, r) - sdRoundedBox(p - float2(eps, 0.0), halfSize, r);
            float dy = sdRoundedBox(p + float2(0.0, eps), halfSize, r) - sdRoundedBox(p - float2(0.0, eps), halfSize, r);
            float2 grad = normalize(float2(dx, dy) + float2(1e-4));

            float rimDepth = clamp(-dist / max(r, 14.0), 0.0, 1.0);
            float lensCurve = pow(1.0 - rimDepth, 2.2);
            float2 refractOffset = -grad * lensCurve * (14.0 * refractStrength);
            float2 disp = grad * lensCurve * (1.35 * chromaticAberration * refractStrength);

            float2 uvG = clamp(fragCoord + refractOffset, float2(0.0), size);
            float2 uvR = clamp(fragCoord + refractOffset + disp, float2(0.0), size);
            float2 uvB = clamp(fragCoord + refractOffset - disp, float2(0.0), size);

            half4 cR = content.eval(uvR);
            half4 cG = content.eval(uvG);
            half4 cB = content.eval(uvB);
            half3 refracted = half3(cR.r, cG.g, cB.b);

            float luma = dot(refracted, half3(0.299, 0.587, 0.114));
            refracted = mix(half3(luma), refracted, mix(1.04, 1.08, 1.0 - isDark));

            half3 glassBody = mix(refracted, half3(tintColor.rgb), half(tintColor.a));

            float edgeBand = smoothstep(-2.2, 0.0, dist);
            float topLight = clamp(-grad.y * 0.65 + 0.35, 0.0, 1.0);
            float bottomRim = clamp(grad.y * 0.55 + 0.15, 0.0, 1.0);

            half3 specularColor = mix(half3(1.0, 1.0, 1.0), half3(0.95, 0.96, 0.99), half(isDark));
            float rimHighlight = edgeBand * mix(0.24, 0.80, topLight) * highlightAlpha;
            float innerSheen = pow(1.0 - rimDepth, 4.0) * 0.08 * highlightAlpha * (0.4 + 0.6 * topLight);
            float bottomShadow = edgeBand * bottomRim * mix(0.10, 0.28, isDark);

            // Cursor-reactive soft specular glint on the glass lens.
            float pDist = length(fragCoord - pointerLocal);
            float glint = exp(-pDist * 0.011) * pointerActive * mix(0.11, 0.16, isDark) * highlightAlpha;
            float rimGlint = edgeBand * exp(-pDist * 0.008) * pointerActive * 0.36 * highlightAlpha;

            glassBody += specularColor * half(rimHighlight + innerSheen + glint + rimGlint);
            glassBody -= half3(bottomShadow);

            float aa = 1.0 - smoothstep(-1.1, 0.6, dist);
            half3 premul = clamp(glassBody, 0.0, 1.0) * half(aa);
            return half4(premul, half(aa));
        }
    """

    private const val MINIMAL_CANVAS_SKSL = """
        uniform float2 size;
        uniform float isDark;

        float hash12(float2 p) {
            float3 p3 = fract(float3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        half4 main(float2 fragCoord) {
            float2 safeSize = max(size, float2(1.0));
            float2 uv = fragCoord / safeSize;

            // Crisp architectural obsidian (dark) / gallery stone-white (light) with gentle depth.
            float2 centered = (uv - float2(0.5, 0.28)) * float2(1.0, safeSize.y / max(safeSize.x, 1.0));
            float falloff = smoothstep(0.0, 0.95, length(centered));

            half3 darkBase = mix(half3(0.068, 0.072, 0.086), half3(0.038, 0.041, 0.050), half(falloff));
            half3 lightBase = mix(half3(0.968, 0.970, 0.978), half3(0.922, 0.927, 0.941), half(falloff));
            half3 col = mix(lightBase, darkBase, half(isDark));

            // Fine isotropic micro-grain to eliminate banding without visual noise.
            float grain = (hash12(fragCoord) - 0.5) * mix(0.008, 0.012, isDark);
            col += half3(grain);

            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    private const val MORPH_PILL_SKSL = """
        uniform float2 size;
        uniform float cornerRadius;
        uniform float4 baseColor;
        uniform float4 glowColor;
        uniform float velocityNorm;
        uniform float timeSec;

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 fragCoord) {
            float2 halfSize = max(size * 0.5, float2(1.0));
            float r = min(cornerRadius, min(halfSize.x, halfSize.y));
            float2 p = fragCoord - halfSize;

            float d = sdRoundedBox(p, halfSize, r);
            if (d > 1.0) return half4(0.0);

            float2 uv = fragCoord / max(size, float2(1.0));
            float sheenWave = 0.5 + 0.5 * sin(uv.x * 3.14159 + timeSec * 1.4);
            float topRim = smoothstep(0.60, 0.0, uv.y);
            float edge = exp(-abs(d + 0.85) * 1.85);

            half3 col = mix(baseColor.rgb, glowColor.rgb, half(0.24 + 0.30 * velocityNorm + 0.08 * sheenWave));
            col += half3(1.0) * half(topRim * (0.14 + 0.14 * velocityNorm));
            col += half3(1.0) * half(edge * 0.24);

            float aa = 1.0 - smoothstep(-1.15, 0.65, d);
            float finalA = clamp(baseColor.a, 0.0, 1.0) * aa;
            return half4(clamp(col, 0.0, 1.0) * half(finalA), half(finalA));
        }
    """

    val glassEffect: RuntimeEffect by lazy {
        RuntimeEffect.makeForShader(LIQUID_GLASS_SKSL)
    }

    val minimalCanvasEffect: RuntimeEffect by lazy {
        RuntimeEffect.makeForShader(MINIMAL_CANVAS_SKSL)
    }

    val morphPillEffect: RuntimeEffect by lazy {
        RuntimeEffect.makeForShader(MORPH_PILL_SKSL)
    }

    fun createMinimalCanvasShader(widthPx: Float, heightPx: Float, isDark: Boolean): org.jetbrains.skia.Shader {
        val builder = RuntimeShaderBuilder(minimalCanvasEffect).apply {
            uniform("size", widthPx.coerceAtLeast(2f), heightPx.coerceAtLeast(2f))
            uniform("isDark", if (isDark) 1f else 0f)
        }
        return builder.makeShader()
    }

    fun createGlassBackdropFilter(
        widthPx: Float,
        heightPx: Float,
        cornerRadiusPx: Float,
        blurSigma: Float,
        refractStrength: Float,
        chromaticAberration: Float,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        tintA: Float,
        highlightAlpha: Float,
        isDark: Boolean,
        pointerLocalX: Float = -1000f,
        pointerLocalY: Float = -1000f,
        pointerActive: Float = 0f,
    ): ImageFilter {
        val builder = RuntimeShaderBuilder(glassEffect).apply {
            uniform("size", widthPx.coerceAtLeast(2f), heightPx.coerceAtLeast(2f))
            uniform("cornerRadius", cornerRadiusPx)
            uniform("refractStrength", refractStrength)
            uniform("chromaticAberration", chromaticAberration)
            uniform("tintColor", tintR, tintG, tintB, tintA)
            uniform("highlightAlpha", highlightAlpha)
            uniform("isDark", if (isDark) 1f else 0f)
            uniform("pointerLocal", pointerLocalX, pointerLocalY)
            uniform("pointerActive", pointerActive)
        }
        val blur = ImageFilter.makeBlur(blurSigma, blurSigma, FilterTileMode.CLAMP)
        return ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = builder,
            shaderName = "content",
            input = blur,
        )
    }

    fun pdfColorFilter(mode: PdfColorMode): ColorFilter? = when (mode) {
        PdfColorMode.LIGHT -> null
        PdfColorMode.DARK -> ColorFilter.makeMatrix(
            ColorMatrix(
                -0.90f, 0f, 0f, 0f, 0.94f,
                0f, -0.90f, 0f, 0f, 0.95f,
                0f, 0f, -0.88f, 0f, 0.97f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        PdfColorMode.SEPIA -> ColorFilter.makeMatrix(
            ColorMatrix(
                0.96f, 0.04f, 0.0f, 0f, 0.0f,
                0.02f, 0.90f, 0.0f, 0f, 0.0f,
                0.0f, 0.02f, 0.76f, 0f, 0.0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
}
