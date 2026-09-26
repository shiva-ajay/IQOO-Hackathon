// Ported from Libraries.dev thinking-orbs (MIT, github.com/Jakubantalik/Libraries.dev).
// See LICENSE-libraries-dev.txt in this folder.
//
// A faithful port of two of the library's sphere-lattice modes
// (src/engine/lattice.ts, src/engine/core.ts, ports/ios/.../Lattice.swift):
//   Thinking  -> `searching` (globe): a lat/long dot sphere spinning while a
//                scan meridian ripples through it as a size + ink wave.
//   Listening -> `listening` (wave): the rings breathe in and out as a
//                rolling two-tempo waveform.
//   Idle      -> the globe lattice with the scan switched off, turning very
//                slowly and dimmed.
// Geometry, preset multipliers (state x size bucket) and the tinted ink ramp
// are the library's; the per-dot math is unchanged. States cross-fade: the
// globe morphs its parameters between Idle and Thinking, and the wave lattice
// cross-fades in over it for Listening.
package com.fixlens.ui.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Assistant activity, mapped to the library's closest named states. */
enum class OrbState { Idle, Listening, Thinking }

/**
 * Dotted 3D thinking orb. Reads at 24-40 dp on a dark translucent panel.
 * The frame loop only invalidates drawing; it never recomposes.
 */
@Composable
fun ThinkingOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = FxColors.Amber,
) {
    val spec = remember(size) { OrbSpec.forSize(size.value) }
    val engine = remember(spec) { OrbEngine(spec) }

    val ease = tween<Float>(durationMillis = 520)
    val wIdle = animateFloatAsState(if (state == OrbState.Idle) 1f else 0f, ease, label = "orbIdle")
    val wListen = animateFloatAsState(if (state == OrbState.Listening) 1f else 0f, ease, label = "orbListen")
    val wThink = animateFloatAsState(if (state == OrbState.Thinking) 1f else 0f, ease, label = "orbThink")

    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                engine.advance(dt, wIdle.value, wListen.value, wThink.value)
                tick.longValue = now
            }
        }
    }

    Canvas(
        modifier
            .size(size)
            .semantics {
                contentDescription = when (state) {
                    OrbState.Idle -> "Fixy is idle"
                    OrbState.Listening -> "Fixy is listening"
                    OrbState.Thinking -> "Fixy is thinking"
                }
            },
    ) {
        tick.longValue // subscribe this draw pass to the frame clock
        engine.draw(this, color, wIdle, wListen, wThink)
    }
}

// ── Presets (src/presets.ts + src/engine/profiles.ts) ────────────────────────

/** Library OrbSize buckets: 20 / 32 / 64 px presets, tuned per size. */
private class OrbSpec(
    val preset: Float,
    // globe (searching)
    val gLat: Int, val gLon: Int, val gRBase: Float, val gRDepth: Float,
    val gSpeed: Float, val gScanMul: Float,
    // wave (listening)
    val wRings: Int, val wLon: Int, val wRBase: Float, val wRDepth: Float, val wSpeed: Float,
) {
    companion object {
        /** Slight radius lift over the library (its `dotSize` prop) so the dots
         *  hold up on a phone panel at 24-40 dp. */
        private const val DOT_SIZE = 1.2f

        fun forSize(dp: Float): OrbSpec {
            val bucket = when {
                dp <= 26f -> 20
                dp <= 48f -> 32
                else -> 64
            }
            // PRESETS[mode][size] = speed, count, size (+ extra)
            val g = when (bucket) {
                20 -> floatArrayOf(2.665f, 0.105f, 1.75f, 4.335f)
                32 -> floatArrayOf(2.3803f, 0.1839f, 1.4769f, 4.2301f)
                else -> floatArrayOf(2.015f, 0.42f, 1.15f, 4.08f)
            }
            val w = when (bucket) {
                20 -> floatArrayOf(3.998f, 0.105f, 1.6f)
                32 -> floatArrayOf(4.1512f, 0.169f, 1.3232f)
                else -> floatArrayOf(4.388f, 0.341f, 1f)
            }
            // scaleCounts: paired lattice knobs each take sqrt(count).
            val gRt = sqrt(g[1])
            val wRt = sqrt(w[1])
            return OrbSpec(
                preset = bucket.toFloat(),
                gLat = max(2, jsRound(17f * gRt)), gLon = max(2, jsRound(44f * gRt)),
                gRBase = 0.6f * g[2] * DOT_SIZE, gRDepth = 1.7f * g[2] * DOT_SIZE,
                gSpeed = g[0], gScanMul = g[3],
                wRings = max(2, jsRound(15f * wRt)), wLon = max(2, jsRound(40f * wRt)),
                wRBase = 0.6f * w[2] * DOT_SIZE, wRDepth = 1.7f * w[2] * DOT_SIZE, wSpeed = w[0],
            )
        }

        private fun jsRound(v: Float): Int = kotlin.math.floor(v + 0.5f).toInt()
    }
}

// ── Engine ───────────────────────────────────────────────────────────────────

private const val R_MIN = 0.3f
private const val RS_POW = 0.6f
private const val INK_FAR = 0.62f
private const val INK_SPAN = 0.54f
private const val DIM_BASE = 0.45f
/** Idle turns the globe at this fraction of the Thinking speed. */
private const val IDLE_SPEED = 0.1f
private const val IDLE_ALPHA = 0.5f

/** Preallocated, reusable dot list: struct-of-arrays plus a draw order. */
private class DotBuffer(capacity: Int) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val z = FloatArray(capacity)
    val r = FloatArray(capacity)
    val w = FloatArray(capacity)
    val a = FloatArray(capacity)
    val order = IntArray(capacity) { it }
    var n = 0

    /** z-sort far -> near. Insertion sort: the order barely changes between
     *  frames, so this is close to linear. */
    fun sortByZ() {
        for (i in 1 until n) {
            val key = order[i]
            val kz = z[key]
            var j = i - 1
            while (j >= 0 && z[order[j]] > kz) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = key
        }
    }
}

private fun latticeCount(rings: Int, lonDensity: Int): Int {
    var total = 0
    for (li in 0..rings) {
        val lat = -PI / 2 + (li.toDouble() / rings) * PI
        total += max(1, kotlin.math.round(abs(cos(lat)) * lonDensity).toInt())
    }
    return total
}

private class OrbEngine(val spec: OrbSpec) {
    private val globe = DotBuffer(latticeCount(spec.gLat, spec.gLon)).also { it.n = it.order.size }
    private val wave = DotBuffer(latticeCount(spec.wRings, spec.wLon)).also { it.n = it.order.size }
    private val rs = (spec.preset / 300f).pow(RS_POW)

    // Each lattice keeps its own integrated clock, so speed changes between
    // states never jump the phase. Start a little in so t=0 isn't degenerate.
    private var tGlobe = 0.6f
    private var tWave = 0.6f
    private var activity = 0f // 0 = idle globe, 1 = scanning globe

    fun advance(dt: Float, wIdle: Float, wListen: Float, wThink: Float) {
        val g = wIdle + wThink
        activity = if (g > 0.001f) wThink / g else activity
        val gSpeed = spec.gSpeed * (IDLE_SPEED + (1f - IDLE_SPEED) * activity)
        if (g > 0.001f) tGlobe += dt * gSpeed
        if (wListen > 0.001f) tWave += dt * spec.wSpeed
    }

    fun draw(scope: DrawScope, tint: Color, wIdle: State<Float>, wListen: State<Float>, wThink: State<Float>) {
        val i = wIdle.value
        val l = wListen.value
        val t = wThink.value
        val zoom = scope.size.minDimension / spec.preset
        scope.scale(zoom, zoom, pivot = Offset.Zero) {
            val gw = i + t
            if (gw > 0.004f) {
                frameGlobe(tGlobe, activity)
                val alpha = gw * (IDLE_ALPHA + (1f - IDLE_ALPHA) * activity)
                paint(this, globe, tint, alpha)
            }
            if (l > 0.004f) {
                frameWave(tWave)
                paint(this, wave, tint, l)
            }
        }
    }

    /** `frameGlobe` (searching). [k] blends the scan in: 0 = idle, 1 = full. */
    private fun frameGlobe(t: Float, k: Float) {
        val size = spec.preset
        val spin = 0.5f
        val cx = size / 2f
        val cy = size / 2f
        val radius = (size / 2f) * 0.82f
        val tilt = 0.4f + 0.06f * sin(t * 0.35f)
        val yaw = t * spin
        val st = sin(tilt); val ct = cos(tilt); val sy = sin(yaw); val cyw = cos(yaw)
        val scan = t * (spin + (1.7f - spin) * spec.gScanMul)
        val dimBase = 1f + (DIM_BASE - 1f) * k
        val rBoost = k
        val b = globe
        var idx = 0
        val lat = spec.gLat
        for (li in 0..lat) {
            val la = (-PI / 2 + (li.toDouble() / lat) * PI).toFloat()
            val cosLat = cos(la)
            val sinLat = sin(la)
            val lonCount = max(1, kotlin.math.round(abs(cosLat) * spec.gLon).toInt())
            for (lj in 0 until lonCount) {
                val lon = (lj.toFloat() / lonCount) * 2f * PI.toFloat()
                val x = cosLat * cos(lon)
                val y = sinLat
                val z = cosLat * sin(lon)
                // makeProj: yaw about Y, then tilt about X, orthographic
                val x1 = x * cyw + z * sy
                val z1 = -x * sy + z * cyw
                val y1 = y * ct - z1 * st
                val z2 = y * st + z1 * ct
                val depth = (z2 + 1f) / 2f
                val d = angleDelta(lon + t * spin, scan)
                val boost = exp(-(d * d) / 0.18f) * max(0f, z2)
                b.x[idx] = cx + x1 * radius
                b.y[idx] = cy - y1 * radius
                b.z[idx] = z2
                b.r[idx] = max(R_MIN, (spec.gRBase + spec.gRDepth * depth + rBoost * boost) * rs)
                b.w[idx] = INK_FAR - INK_SPAN * depth
                b.a[idx] = dimBase + (1f - dimBase) * min(1f, boost)
                idx++
            }
        }
        b.n = idx
        b.sortByZ()
    }

    /** `frameWave` (listening): a waveform rolls through the rings. */
    private fun frameWave(t: Float) {
        val size = spec.preset
        val cx = size / 2f
        val cy = size / 2f
        val r0 = (size / 2f) * 0.874f
        val yaw = t * 0.18f
        val tilt = 0.38f
        val st = sin(tilt); val ct = cos(tilt); val sy = sin(yaw); val cyw = cos(yaw)
        val b = wave
        var idx = 0
        val rings = spec.wRings
        for (ri in 0..rings) {
            val la = (-PI / 2 + (ri.toDouble() / rings) * PI).toFloat()
            val cosLat = cos(la)
            val sinLat = sin(la)
            // two waves, different tempi: organic, never quite repeating
            val wv = 0.62f * sin(t * 2.1f - ri * 0.52f) + 0.38f * sin(t * 1.27f + ri * 0.83f)
            val rr = r0 * (0.88f + 0.105f * wv)
            val crest = max(0f, wv)
            val lonCount = max(1, kotlin.math.round(abs(cosLat) * spec.wLon).toInt())
            for (lj in 0 until lonCount) {
                val lon = (lj.toFloat() / lonCount) * 2f * PI.toFloat()
                val x = cosLat * cos(lon) * rr
                val y = sinLat * rr
                val z = cosLat * sin(lon) * rr
                val x1 = x * cyw + z * sy
                val z1 = -x * sy + z * cyw
                val y1 = y * ct - z1 * st
                val z2 = y * st + z1 * ct
                val depth = (z2 / r0 + 1f) / 2f
                b.x[idx] = cx + x1
                b.y[idx] = cy - y1
                b.z[idx] = z2
                b.r[idx] = max(R_MIN, (spec.wRBase + spec.wRDepth * depth) * (1f + 0.4f * crest) * rs)
                b.w[idx] = 0.66f - 0.56f * depth - 0.1f * crest
                b.a[idx] = 1f
                idx++
            }
        }
        b.n = idx
        b.sortByZ()
    }

    /** core.ts `paint` with a tint on a dark substrate: the ink ramp becomes
     *  a ramp on the tint toward black, so near dots read bright. */
    private fun paint(scope: DrawScope, b: DotBuffer, tint: Color, alpha: Float) {
        for (k in 0 until b.n) {
            val i = b.order[k]
            val a = b.a[i] * alpha
            if (a < 0.02f) continue
            val lum = 1f - b.w[i].coerceIn(0f, 1f)
            scope.drawCircle(
                color = Color(tint.red * lum, tint.green * lum, tint.blue * lum, a.coerceAtMost(1f)),
                radius = b.r[i],
                center = Offset(b.x[i], b.y[i]),
            )
        }
    }
}

/** Shortest signed angular distance, wrapped to (-pi, pi]. */
private fun angleDelta(a: Float, b: Float): Float = atan2(sin(a - b), cos(a - b))
