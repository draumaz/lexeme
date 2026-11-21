package io.github.draumaz.lexeme.ui.components

import android.annotation.SuppressLint
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Whether this device has Android 17 QPR2's backdrop render effects; anything older (back to
 * minSdk 29) gets plain tonal fills. SDK_INT_FULL itself only exists from 36, hence the guard.
 */
val GlassSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2

/**
 * Frosted glass: fills [shape] with [tint] and blurs whatever was already drawn behind it by
 * [blur], through QPR2's `RenderNode.setBackdropRenderEffect`.
 *
 * The tint stays a Material colour role at partial alpha rather than a neutral white wash,
 * so a glass surface still reads as the container it replaces (the search scallop is
 * still primaryContainer) and keeps the same on-colour contrast. Glass is a treatment of a tonal surface, not a new surface.
 *
 * The content is recorded into a platform [RenderNode] of its own because Compose's
 * graphicsLayer has no backdrop slot: its renderEffect only filters the layer's own pixels.
 * The node is drawn back into Compose's recording canvas, so it composites in place and
 * moves with any graphicsLayer above it. Without hardware acceleration (or before QPR2)
 * this falls back to [fallback], an opaque-enough tint that text behind can't muddle it.
 *
 * [blur] is read at draw time, so animating it re-records only this node, never recomposes.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color,
    fallback: Color = tint,
    blur: () -> Dp = DefaultBlur,
): Modifier = this then GlassElement(shape, tint, fallback, blur)

/** Shared, so the element compares equal across recompositions and doesn't redraw for nothing. */
private val DefaultBlur: () -> Dp = { GlassDefaults.Blur }

object GlassDefaults {
    /** Strong enough that list text behind turns to colour, not readable ghosts. */
    val Blur = 24.dp
}

private data class GlassElement(
    val shape: Shape,
    val tint: Color,
    val fallback: Color,
    val blur: () -> Dp,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, tint, fallback, blur)
    override fun update(node: GlassNode) {
        node.shape = shape
        node.tint = tint
        node.fallback = fallback
        node.blur = blur
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
        properties["shape"] = shape
        properties["tint"] = tint
    }
}

private class GlassNode(
    var shape: Shape,
    var tint: Color,
    var fallback: Color,
    var blur: () -> Dp,
) : Modifier.Node(), DrawModifierNode {
    private var renderNode: RenderNode? = null
    private var lastBlurPx = -1f
    /** The last outline built, with what it was built for (plain fields avoid per-frame allocations). */
    private var cachedSize = Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedShape: Shape? = null
    private var cachedOutline: Outline? = null
    /** The outline the render node already clips to, so it isn't rebuilt every frame. */
    private var appliedOutline: Outline? = null

    override fun onDetach() {
        renderNode?.discardDisplayList()
        renderNode = null
        lastBlurPx = -1f
        cachedSize = Size.Unspecified
        cachedDirection = null
        cachedShape = null
        cachedOutline = null
        appliedOutline = null
    }

    // setBackdropRenderEffect is 37.2; every path to it is behind GlassSupported.
    @SuppressLint("NewApi")
    override fun ContentDrawScope.draw() {
        val shapeOutline = if (cachedOutline != null && cachedSize == size && cachedDirection == layoutDirection && cachedShape == shape) {
            cachedOutline!!
        } else {
            shape.createOutline(size, layoutDirection, this).also {
                cachedSize = size
                cachedDirection = layoutDirection
                cachedShape = shape
                cachedOutline = it
            }
        }

        val target = drawContext.canvas.nativeCanvas
        if (!GlassSupported || target !is RecordingCanvas || size.isEmpty()) {
            drawOutline(shapeOutline, fallback)
            drawContent()
            return
        }

        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        val rn = renderNode ?: RenderNode("glass").also { renderNode = it }
        rn.setPosition(0, 0, w, h)
        if (shapeOutline !== appliedOutline) {
            applyOutline(rn, shapeOutline)
            appliedOutline = shapeOutline
        }

        // A zero radius would still pay for the backdrop pass; drop the effect instead.
        val blurPx = blur().toPx().coerceAtLeast(0f)
        if (blurPx != lastBlurPx) {
            rn.setBackdropRenderEffect(
                if (blurPx < 0.5f) null
                else RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            )
            lastBlurPx = blurPx
        }

        val content = this
        val recording = rn.beginRecording(w, h)
        try {
            draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(recording), size) {
                drawOutline(shapeOutline, tint)
                content.drawContent()
            }
        } finally {
            rn.endRecording()
        }
        target.drawRenderNode(rn)
    }

    /**
     * Clips the node, and with it the backdrop pass, to the shape: otherwise a scalloped or
     * rounded surface would frost a rectangle of the list around itself.
     */
    @SuppressLint("NewApi") // Only reached on QPR2 (see draw); setPath is API 30.
    private fun applyOutline(rn: RenderNode, outline: Outline) {
        val platform = android.graphics.Outline()
        when (outline) {
            is Outline.Rectangle -> platform.setRect(
                outline.rect.left.roundToInt(), outline.rect.top.roundToInt(),
                outline.rect.right.roundToInt(), outline.rect.bottom.roundToInt(),
            )
            is Outline.Rounded -> {
                val r = outline.roundRect
                if (r.topLeftCornerRadius == r.topRightCornerRadius &&
                    r.topLeftCornerRadius == r.bottomLeftCornerRadius &&
                    r.topLeftCornerRadius == r.bottomRightCornerRadius
                ) {
                    platform.setRoundRect(
                        r.left.roundToInt(), r.top.roundToInt(),
                        r.right.roundToInt(), r.bottom.roundToInt(),
                        r.topLeftCornerRadius.x,
                    )
                } else {
                    platform.setPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(r) }.asAndroidPath())
                }
            }
            is Outline.Generic -> platform.setPath(outline.path.asAndroidPath())
        }
        rn.setOutline(platform)
        rn.clipToOutline = true
    }
}

/**
 * Progressive vertical backdrop blur: sharp at the top edge, easing smoothly up to [blur] at the
 * bottom, with a [scrim] of colour fading in alongside it (its alpha is the strength at the
 * bottom). Both are read at draw time, so animating them never recomposes.
 *
 * It's one backdrop pass through a variable-radius Gaussian (a horizontal then a vertical
 * AGSL pass, the radius following the row), rather than stacked blur layers, so there are no
 * seams between steps and no ghosted double image where a sharp layer shows through a soft
 * one. The scrim is mixed in the same shader and dithered, so the long, shallow fade over the
 * surface colour can't band. Before Android 17 QPR2 it's just the scrim, drawn as a gradient
 * along the same curve.
 */
fun Modifier.progressiveBlur(blur: () -> Dp, scrim: () -> Color = { Color.Transparent }): Modifier =
    this then ProgressiveBlurElement(blur, scrim)

/** How far down the band (0 to 1) the blur reaches full strength. */
private const val ProgressiveBlurFullAt = 0.85f

/**
 * One direction of the variable blur. The radius at a pixel is [maxSigma] times a smoothstep
 * down the band; taps sit a quarter sigma apart out to three sigma, so the weights are constant
 * and the spacing never gets coarse enough to ghost. The vertical pass also lays in the scrim.
 */
private const val ProgressiveBlurShader = """
uniform shader content;
uniform float2 size;
uniform float maxSigma;
uniform float2 dir;
uniform float fullAt;
uniform half4 scrim; // premultiplied; alpha is the strength at the bottom
uniform float applyScrim;

float ease(float y) {
    float t = clamp(y / (size.y * fullAt), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

half4 main(float2 p) {
    float sigma = maxSigma * ease(p.y);
    half4 color;
    if (sigma < 0.35) {
        color = content.eval(p);
    } else {
        float2 stepV = dir * (sigma * 0.25);
        float2 lo = float2(0.5);
        float2 hi = size - 0.5;
        half4 acc = half4(0.0);
        float total = 0.0;
        for (int i = -12; i <= 12; i++) {
            float x = float(i) * 0.25;
            float w = exp(-0.5 * x * x);
            acc += content.eval(clamp(p + stepV * float(i), lo, hi)) * half(w);
            total += w;
        }
        color = acc / half(total);
    }
    if (applyScrim > 0.5) {
        float t = clamp(p.y / size.y, 0.0, 1.0);
        half a = half(t * t * (3.0 - 2.0 * t));
        color = color * (1.0 - scrim.a * a) + scrim * a;
        // A whisper of noise, under one 8-bit step, so the fade has no bands.
        float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453)
            + fract(sin(dot(p, float2(39.3467, 11.1351))) * 24634.6345) - 1.0;
        color.rgb += half3(n / 255.0) * color.a;
    }
    return color;
}
"""

private data class ProgressiveBlurElement(
    val blur: () -> Dp,
    val scrim: () -> Color,
) : ModifierNodeElement<ProgressiveBlurNode>() {
    override fun create() = ProgressiveBlurNode(blur, scrim)
    override fun update(node: ProgressiveBlurNode) {
        node.blur = blur
        node.scrim = scrim
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "progressiveBlur"
    }
}

private class ProgressiveBlurNode(
    var blur: () -> Dp,
    var scrim: () -> Color,
) : Modifier.Node(), DrawModifierNode {
    private var renderNode: RenderNode? = null
    private var horizontal: RuntimeShader? = null
    private var vertical: RuntimeShader? = null
    private var lastSigma = -1f
    private var lastScrim = Color.Unspecified
    private var lastW = -1
    private var lastH = -1

    override fun onDetach() {
        renderNode?.discardDisplayList()
        renderNode = null
        horizontal = null
        vertical = null
        lastSigma = -1f
        lastScrim = Color.Unspecified
        lastW = -1
        lastH = -1
    }

    // RuntimeShader is 33 and setBackdropRenderEffect 37.2; both are behind GlassSupported.
    @SuppressLint("NewApi")
    override fun ContentDrawScope.draw() {
        val target = drawContext.canvas.nativeCanvas
        val scrimColor = scrim()
        // Skia's blur radius to sigma, so the dp reads as it does for createBlurEffect.
        val radiusPx = blur().toPx().coerceAtLeast(0f)
        val sigma = if (radiusPx < 0.5f) 0f else radiusPx * 0.57735f + 0.5f
        if (!GlassSupported || target !is RecordingCanvas || size.isEmpty()) {
            if (scrimColor.alpha > 0f) drawRect(scrimGradient(scrimColor))
            drawContent()
            return
        }
        if (sigma == 0f && scrimColor.alpha == 0f) {
            drawContent()
            return
        }

        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        val rn = renderNode ?: RenderNode("progressiveBlur").also { renderNode = it }
        if (w != lastW || h != lastH || sigma != lastSigma || scrimColor != lastScrim) {
            val hs = horizontal ?: RuntimeShader(ProgressiveBlurShader).also { horizontal = it }
            val vs = vertical ?: RuntimeShader(ProgressiveBlurShader).also { vertical = it }
            for ((shader, dx, dy) in listOf(Triple(hs, 1f, 0f), Triple(vs, 0f, 1f))) {
                shader.setFloatUniform("size", w.toFloat(), h.toFloat())
                shader.setFloatUniform("maxSigma", sigma)
                shader.setFloatUniform("dir", dx, dy)
                shader.setFloatUniform("fullAt", ProgressiveBlurFullAt)
            }
            val a = scrimColor.alpha
            hs.setFloatUniform("scrim", 0f, 0f, 0f, 0f)
            hs.setFloatUniform("applyScrim", 0f)
            vs.setFloatUniform("scrim", scrimColor.red * a, scrimColor.green * a, scrimColor.blue * a, a)
            vs.setFloatUniform("applyScrim", 1f)
            rn.setPosition(0, 0, w, h)
            rn.setBackdropRenderEffect(
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(vs, "content"),
                    RenderEffect.createRuntimeShaderEffect(hs, "content"),
                )
            )
            lastW = w; lastH = h; lastSigma = sigma; lastScrim = scrimColor
        }
        // Record an empty display list so the node takes part in the backdrop pass.
        rn.beginRecording(w, h)
        rn.endRecording()
        target.drawRenderNode(rn)
        drawContent()
    }

    /** The fallback scrim: the shader's smoothstep, laid out in enough stops to follow the curve. */
    private fun scrimGradient(color: Color): Brush {
        val stops = Array(17) { i ->
            val t = i / 16f
            t to color.copy(alpha = color.alpha * t * t * (3f - 2f * t))
        }
        return Brush.verticalGradient(*stops)
    }
}
