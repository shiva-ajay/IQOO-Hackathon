// Ported from Libraries.dev voice-glow (MIT, github.com/Jakubantalik/Libraries.dev).
// See LICENSE-libraries-dev.txt in this folder.
//
// Native Compose port of VoiceBeam's `mobile` type (the bottom of a phone
// screen), dark theme. Kept from the library:
//   - voiceDriver.ts envelope: noise gate + soft saturation, one-pole
//     follower with fast attack (0.325 s) / slow release (0.86 s), three
//     synthesised bands that wobble out of phase so lobes ripple instead of
//     pumping, idle breathing (5.2 s) folded under the voice.
//   - The seven-lobe ring (styles.ts `voiceLobes`): soft radial ellipses fanned
//     out from the bottom centre, heights driven by their band, sliding
//     sideways with the `flow` (px/s x level) and wrapping with an edge
//     envelope so nothing pops.
//   - Layers: a wide blurred-looking bloom, the inner lobes, a white-hot core
//     and a bright edge stroke along the bottom, all scaled by `glow`.
// Simplified: one brand colour spread into warm tonal variants instead of the
// 7-colour palettes, no SVG displacement warp, no bent "band" ridge with
// chromatic fringes, no processing sweep. Blur is emulated by the gradients'
// own falloff (no blur pass), which keeps it cheap next to the camera + VLM.
package com.fixlens.ui.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * A soft glow rising from the bottom edge of its bounds, driven by voice.
 *
 * @param level raw 0..1 mic loudness, updated ~30x/s; smoothed internally.
 * @param active when false the glow fades out completely and the frame loop stops.
 */
@Composable
fun VoiceGlow(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = FxColors.Amber,
) {
    val latestLevel = rememberUpdatedState(level)
    val fade = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 450 else 600),
        label = "voiceGlowFade",
    )
    val driver = remember { VoiceDriver() }
    val tick = remember { mutableLongStateOf(0L) }

    // Frame loop only while visible: runs until the fade-out lands on 0.
    LaunchedEffect(active) {
        var last = -1L
        while (active || fade.value > 0f) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                driver.step(dt, latestLevel.value)
                tick.longValue = now
            }
        }
        // Faded out: settle so the next activation rises from silence.
        driver.reset()
    }

    Spacer(
        modifier.drawWithCache {
            val palette = GlowPalette(color)
            onDrawBehind {
                tick.longValue // redraw per frame without recomposing
                val f = fade.value
                if (f > 0.003f) drawGlow(driver, palette, f)
            }
        },
    )
}

// ── Tuning (presets.ts `mobile`, styles.ts `themePresets.dark`) ──────────────

private const val THRESHOLD = 0.015f
private const val ATTACK_S = 0.325f
private const val RELEASE_S = 0.86f
private const val IDLE = 0.18f
private const val BREATHE_S = 5.2f
private const val REACH = 3f
private const val SPREAD = 0.45f
private const val FLOW = 60f // px/s at full level (library px ~ dp at 400 dp width)
private const val LOBE_SPACING = 1.35f
private const val GLOW_WIDTH = 1.15f
private const val GLOW_HEIGHT = 2.1f
private const val SCALE = 1.25f
/** The library authors `mobile` against a ~400 px wide phone screen. */
private const val REFERENCE_WIDTH_DP = 400f

/** styles.ts `voiceLobes`: x offset, width, height (px), band. */
private val LOBE_X = floatArrayOf(0f, -36f, 36f, -72f, 72f, -108f, 108f)
private val LOBE_W = floatArrayOf(74f, 54f, 54f, 48f, 48f, 42f, 42f)
private val LOBE_H = floatArrayOf(46f, 40f, 40f, 32f, 32f, 26f, 26f)
private val LOBE_BAND = intArrayOf(0, 1, 1, 2, 2, 1, 1)
private const val LOBE_SPAN = 36f * 7f

private const val TWO_PI = (2 * PI).toFloat()

/** voiceDriver.ts, reduced to the level-getter path. Plain fields: never snapshot state. */
private class VoiceDriver {
    var level = 0f
    val bands = FloatArray(3)
    var phase = 0f
    var t = 0f

    // Per-frame outputs.
    var glow = 0f
    var h = 0f
    var w = 0f
    val lobeX = FloatArray(7)
    val lobeL = FloatArray(7)

    fun reset() {
        level = 0f
        bands.fill(0f)
    }

    fun step(dt: Float, rawIn: Float) {
        t += dt
        val raw = rawIn.coerceIn(0f, 1f)
        // No spectrum: synthesise three out-of-phase bands from the level.
        val b0 = raw
        val b1 = raw * (0.72f + 0.28f * sin(t * 9.1f))
        val b2 = raw * (0.6f + 0.4f * sin(t * 13.7f + 2f))

        level = follow(level, shape(raw, THRESHOLD), dt, ATTACK_S, RELEASE_S)
        bands[0] = follow(bands[0], shape(b0, THRESHOLD * 0.6f), dt, ATTACK_S, RELEASE_S * 1.15f)
        bands[1] = follow(bands[1], shape(b1, THRESHOLD * 0.6f), dt, ATTACK_S, RELEASE_S * 1.15f)
        bands[2] = follow(bands[2], shape(b2, THRESHOLD * 0.6f), dt, ATTACK_S, RELEASE_S * 1.15f)

        // Idle breathing folded under the voice.
        val breathe = 0.5f + 0.5f * sin(TWO_PI * t / BREATHE_S)
        val eff = level + (1f - level) * IDLE * breathe

        glow = 0.15f + 0.85f * eff
        h = 0.5f + REACH * eff
        w = 0.85f + SPREAD * eff

        // Flow: the lobes slide sideways while a voice is heard.
        val span = LOBE_SPAN * LOBE_SPACING
        phase = (((phase + FLOW * eff * dt) % span) + span) % span
        for (i in 0 until 7) {
            val x = wrapX(LOBE_X[i] * LOBE_SPACING + phase, span)
            lobeX[i] = x
            lobeL[i] = (0.6f + 0.7f * bands[LOBE_BAND[i]]) * edgeEnvelope(x, span)
        }
    }

    private fun shape(raw: Float, threshold: Float): Float {
        if (raw <= threshold) return 0f
        val u = (raw - threshold) / max(0.001f, 1f - threshold)
        return ((1f - exp(-3f * u)) / (1f - exp(-3f))).coerceIn(0f, 1f)
    }

    private fun follow(prev: Float, target: Float, dt: Float, attack: Float, release: Float): Float {
        val tau = if (target > prev) attack else release
        val a = 1f - exp(-dt / max(0.001f, tau))
        return prev + (target - prev) * a
    }

    private fun wrapX(x: Float, span: Float): Float {
        val half = span / 2f
        return (((x + half) % span) + span) % span - half
    }

    private fun edgeEnvelope(x: Float, span: Float): Float {
        val u = x / (span / 2f + 4f)
        return max(0f, 1f - u * u)
    }
}

/**
 * Unit-radius radial brushes, built once per colour/size (drawWithCache).
 * Each is drawn under a translate + scale transform, so the ellipse moves and
 * stretches every frame without allocating a new gradient.
 */
private class GlowPalette(base: Color) {
    private fun lobe(c: Color, peak: Float) = Brush.radialGradient(
        0f to c.copy(alpha = peak),
        0.3f to c.copy(alpha = peak * 0.62f),
        0.6f to c.copy(alpha = peak * 0.22f),
        0.85f to c.copy(alpha = peak * 0.05f),
        1f to c.copy(alpha = 0f),
        center = Offset.Zero,
        radius = 1f,
    )

    private val light = base.towardPaper(0.3f)
    private val warm = base.shiftHue(-9f)
    private val deep = base.shiftHue(-16f)
    private val gold = base.shiftHue(7f)

    /** Seven tones, one per lobe (centre first, then pairs), like the palettes. */
    val lobes: Array<Brush> = arrayOf(
        lobe(light, 1f), lobe(base, 1f), lobe(gold, 1f),
        lobe(warm, 1f), lobe(base, 1f), lobe(deep, 1f), lobe(warm, 1f),
    )
    val bloom: Brush = lobe(base, 1f)
    val wash: Brush = lobe(deep, 1f)
    val core: Brush = lobe(FxColors.Paper, 1f)
    val edge: Brush = lobe(base.towardPaper(0.5f), 1f)
}

/** One soft ellipse centred at (cx, cy) with radii (rx, ry). */
private fun DrawScope.ellipse(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float) {
    if (rx <= 0.5f || ry <= 0.5f || alpha <= 0.003f) return
    translate(cx, cy) {
        scale(rx, ry, pivot = Offset.Zero) {
            drawRect(brush, topLeft = Offset(-1f, -1f), size = Size(2f, 2f), alpha = alpha.coerceAtMost(1f))
        }
    }
}

private fun DrawScope.drawGlow(d: VoiceDriver, p: GlowPalette, fade: Float) {
    val width = size.width
    val bottom = size.height
    // Library px -> screen px, keeping the mobile preset's proportions at any width.
    val k = width / REFERENCE_WIDTH_DP * SCALE
    val cx = width / 2f
    val glow = d.glow * fade
    val w = d.w
    val h = d.h

    clipRect {
        // Broad wash: the full-width band the lobes sit in.
        ellipse(p.wash, cx, bottom, width * (0.62f + 0.12f * w), 58f * k * h, 0.30f * glow)

        // Bloom (library: inner five lobes, blurred 10 px, opacity 0.89). The
        // blur is folded into larger, fainter ellipses.
        for (i in 0 until 5) {
            val l = d.lobeL[i]
            if (l <= 0.01f) continue
            ellipse(
                p.bloom,
                cx + d.lobeX[i] * w * k,
                bottom,
                LOBE_W[i] * GLOW_WIDTH * 1.7f * w * k,
                LOBE_H[i] * GLOW_HEIGHT * 1.6f * h * l * k,
                0.26f * glow,
            )
        }

        // Inner light: all seven lobes, each riding its band.
        for (i in 0 until 7) {
            val l = d.lobeL[i]
            if (l <= 0.01f) continue
            ellipse(
                p.lobes[i],
                cx + d.lobeX[i] * w * k,
                bottom,
                LOBE_W[i] * GLOW_WIDTH * w * k,
                LOBE_H[i] * GLOW_HEIGHT * h * l * k,
                0.55f * glow,
            )
        }

        // White-hot core at the centre of the edge.
        ellipse(p.core, cx, bottom, 34f * k * w, 16f * k * h, 0.38f * glow)

        // Edge stroke: a thin bright line hugging the bottom edge.
        ellipse(p.edge, cx, bottom, width * (0.34f + 0.16f * w), 3.5f * k, 0.9f * glow)
    }
}
