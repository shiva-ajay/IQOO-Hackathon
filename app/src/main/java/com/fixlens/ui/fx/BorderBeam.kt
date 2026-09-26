// Ported from Libraries.dev border-beam (MIT, github.com/Jakubantalik/Libraries.dev).
// See LICENSE-libraries-dev.txt in this folder.
//
// Native Compose port of the `md` rotate beam (dark theme). The web version
// spins a conic-gradient mask (`--beam-angle`) over three layers: an inner
// glow, the stroke ring (colour window + white highlight) and a blurred bloom.
// Here each layer is one android SweepGradient whose local matrix is rotated
// per frame; the conic stop tables are copied from spec/beam-spec.json.
//
// Simplified vs the library: one brand colour instead of the 8-blob colourful
// palettes, no hue-rotate / saturate filter matrices, and the 8 px bloom blur
// and inner edge fade are approximated with stacked wide strokes (no blur pass).
package com.fixlens.ui.fx

import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A soft light segment travelling around [shape]'s border, over a faint static
 * hairline, while [active]. Fades in (0.6 s) and out (0.5 s) like the library.
 * Draws nothing and runs no frame loop once fully faded out.
 */
fun Modifier.borderBeam(
    active: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = FxColors.Amber,
    strokeWidth: Dp = 1.5.dp,
): Modifier = this then BorderBeamElement(active, shape, color, strokeWidth)

// ── Tuning (spec/beam-spec.json → rotate, dark theme) ───────────────────────

/** Seconds per revolution. The library's `md` default is 1.96 s for a ~350 px
 *  card; our panel spans the screen, so it is slowed to keep the same pace
 *  along the edge and a calmer read. */
private const val BEAM_PERIOD_S = 3.2

/** CSS `ease`, the library's fade easing. */
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private const val FADE_IN_MS = 600
private const val FADE_OUT_MS = 500

/** `beamMaskStops`: the coloured window (long tail, short leading edge). */
private val WINDOW_STOPS = floatArrayOf(
    0f, 0f, 30f, 0f, 36f, 0.1f, 44f, 0.35f, 52f, 1f, 80f, 1f, 86f, 0.35f, 92f, 0.1f, 95f, 0f, 100f, 0f,
)

/** `whiteGradientStops.dark`: the bright highlight riding inside the window. */
private val CORE_STOPS = floatArrayOf(
    0f, 0f, 54f, 0f, 57f, 0.1f, 60f, 0.3f, 63f, 0.6f, 66f, 0.75f, 69f, 0.6f, 72f, 0.3f, 75f, 0.1f, 78f, 0f, 100f, 0f,
)

/** `bloomGradientStops.dark`: the hot head of the beam, painted wide and soft. */
private val BLOOM_STOPS = floatArrayOf(
    0f, 0f, 58f, 0f, 62f, 0.03f, 65f, 0.08f, 67f, 0.2f, 69f, 0.45f, 70f, 0.85f, 70.5f, 0.85f,
    71.5f, 0.45f, 73f, 0.2f, 75f, 0.08f, 78f, 0.03f, 82f, 0f, 100f, 0f,
)

private fun sweep(color: Color, stops: FloatArray): SweepGradient {
    val n = stops.size / 2
    val colors = IntArray(n)
    val positions = FloatArray(n)
    for (i in 0 until n) {
        positions[i] = stops[2 * i] / 100f
        colors[i] = color.copy(alpha = color.alpha * stops[2 * i + 1]).toArgb()
    }
    return SweepGradient(0f, 0f, colors, positions)
}

/** A brush over a shader we own and rotate in place (no per-frame allocation). */
private class RotatingBrush(val shader: Shader) : ShaderBrush() {
    override fun createShader(size: Size): Shader = shader
}

private data class BorderBeamElement(
    val active: Boolean,
    val shape: Shape,
    val color: Color,
    val strokeWidth: Dp,
) : ModifierNodeElement<BorderBeamNode>() {
    override fun create() = BorderBeamNode(active, shape, color, strokeWidth)

    override fun update(node: BorderBeamNode) = node.update(active, shape, color, strokeWidth)

    override fun InspectorInfo.inspectableProperties() {
        name = "borderBeam"
        properties["active"] = active
        properties["shape"] = shape
        properties["color"] = color
        properties["strokeWidth"] = strokeWidth
    }
}

private class BorderBeamNode(
    private var active: Boolean,
    private var shape: Shape,
    private var color: Color,
    private var strokeWidth: Dp,
) : Modifier.Node(), DrawModifierNode {

    private val fade = Animatable(if (active) 1f else 0f)
    private val activeFlow = MutableStateFlow(active)
    private var startNanos = -1L
    private var nowNanos = 0L

    // Shaders: rebuilt only when the colour changes; rotated per frame.
    private val matrix = Matrix()
    private var brushColor: Color? = null
    private lateinit var windowBrush: RotatingBrush
    private lateinit var coreBrush: RotatingBrush
    private lateinit var bloomBrush: RotatingBrush
    private var hairline = Color.Unspecified

    // Geometry: rebuilt only when size / shape / density / direction change.
    private var geoSize = Size.Unspecified
    private var geoDir = LayoutDirection.Ltr
    private var geoDensity = 0f
    private var geoValid = false
    private val strokePath = Path() // outline inset by half the stroke
    private val clipOutline = Path() // full outline, clips the inner glow
    private var coreStroke = Stroke(1f)
    private var bloomMid = Stroke(1f)
    private var bloomWide = Stroke(1f)
    private var innerMid = Stroke(1f)
    private var innerWide = Stroke(1f)

    fun update(active: Boolean, shape: Shape, color: Color, strokeWidth: Dp) {
        if (shape != this.shape || strokeWidth != this.strokeWidth) geoValid = false
        this.shape = shape
        this.color = color
        this.strokeWidth = strokeWidth
        this.active = active
        activeFlow.value = active
    }

    override fun onAttach() {
        coroutineScope.launch {
            activeFlow.collectLatest { on ->
                launch {
                    fade.animateTo(
                        if (on) 1f else 0f,
                        tween(if (on) FADE_IN_MS else FADE_OUT_MS, easing = CssEase),
                    )
                }
                // Frame loop only while visible: runs until the fade-out lands on 0.
                while (on || fade.value > 0f) {
                    withFrameNanos { t ->
                        if (startNanos < 0) startNanos = t
                        nowNanos = t
                    }
                    invalidateDraw()
                }
                invalidateDraw()
            }
        }
    }

    override fun onDetach() {
        startNanos = -1L
    }

    private fun ContentDrawScope.ensureGeometry() {
        if (geoValid && geoSize == size && geoDir == layoutDirection && geoDensity == density) return
        val sw = strokeWidth.toPx().coerceAtLeast(0.5f)
        val half = sw / 2f
        strokePath.reset()
        val inset = Size((size.width - sw).coerceAtLeast(0f), (size.height - sw).coerceAtLeast(0f))
        strokePath.addOutline(shape.createOutline(inset, layoutDirection, this))
        strokePath.translate(Offset(half, half))
        clipOutline.reset()
        clipOutline.addOutline(shape.createOutline(size, layoutDirection, this))
        coreStroke = Stroke(sw)
        bloomMid = Stroke(sw * 2.5f)
        bloomWide = Stroke(sw * 5f)
        innerMid = Stroke(8.dp.toPx())
        innerWide = Stroke(18.dp.toPx())
        geoSize = size
        geoDir = layoutDirection
        geoDensity = density
        geoValid = true
    }

    private fun ensureBrushes() {
        if (brushColor == color) return
        windowBrush = RotatingBrush(sweep(color, WINDOW_STOPS))
        // The library's highlight is white; lean it toward paper so it stays warm.
        coreBrush = RotatingBrush(sweep(color.towardPaper(0.6f), CORE_STOPS))
        bloomBrush = RotatingBrush(sweep(color.towardPaper(0.2f), BLOOM_STOPS))
        hairline = color.towardPaper(0.55f)
        brushColor = color
    }

    override fun ContentDrawScope.draw() {
        val f = fade.value
        if (f <= 0.001f || size.minDimension <= 0f) {
            drawContent()
            return
        }
        ensureGeometry()
        ensureBrushes()

        // CSS conic gradients start at 12 o'clock, Android sweeps at 3 o'clock.
        val elapsed = if (startNanos < 0) 0.0 else (nowNanos - startNanos) / 1e9
        val turn = ((elapsed / BEAM_PERIOD_S) % 1.0).toFloat()
        matrix.setRotate(turn * 360f - 90f)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        windowBrush.shader.setLocalMatrix(matrix)
        coreBrush.shader.setLocalMatrix(matrix)
        bloomBrush.shader.setLocalMatrix(matrix)

        // Behind content: inner light (::before) clipped inside the shape, then bloom.
        clipPath(clipOutline) {
            drawPath(strokePath, windowBrush, alpha = 0.07f * f, style = innerWide)
            drawPath(strokePath, windowBrush, alpha = 0.10f * f, style = innerMid)
        }
        drawPath(strokePath, bloomBrush, alpha = 0.14f * f, style = bloomWide)
        drawPath(strokePath, bloomBrush, alpha = 0.26f * f, style = bloomMid)

        drawContent()

        // On top: static hairline, the coloured window, the bright core.
        drawPath(strokePath, hairline, alpha = 0.14f * f, style = coreStroke)
        drawPath(strokePath, windowBrush, alpha = 0.85f * f, style = coreStroke)
        drawPath(strokePath, coreBrush, alpha = f, style = coreStroke)
    }
}
