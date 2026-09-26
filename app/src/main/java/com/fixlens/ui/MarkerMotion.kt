package com.fixlens.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.fixlens.tracking.MarkerState
import com.fixlens.ui.fx.easeInSine
import com.fixlens.ui.fx.easeOutCubic
import com.fixlens.ui.fx.hash01
import com.fixlens.ui.fx.lerp
import com.fixlens.ui.fx.window
import com.fixlens.vision.BoxMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The marker's choreography, shared by the marker layer (under the cards) and the hand-off layer (over them).
 *
 * Every part the VLM points at gets a [Launch] cue. A comet leaves Fixy's orb (the "slow brain"), homes in on the
 * part's live tracked position (the "fast eyes") and the lock-on plays where it lands. A quiet seed (a silent
 * re-ground) skips the comet and gets a short re-lock. All timing derives from one frame clock, [now], which ticks
 * only while there is a marker and is read only in draw lambdas, so the animation redraws without recomposing.
 */
@Stable
class MarkerMotion internal constructor() {
    internal class Launch(val atMs: Long, val flightMs: Int, val from: Offset?, val bend: Float) {
        val impactMs get() = atMs + flightMs
    }

    private val clock = mutableLongStateOf(0L)
    internal val now: Long get() = clock.longValue
    internal val launches = mutableStateListOf<Launch>()
    internal var seedId = -1
        private set
    internal var quiet = false
        private set
    /** Fixy's orb, where comets launch from; its centre is read (in root coordinates) at each launch. */
    internal var orb: LayoutCoordinates? = null

    /**
     * ms since part [i] of seed [seed] landed; negative while its comet is in flight, or while the part isn't cued
     * yet (a frame can draw a new seed or part just before [onMarker] sees it: it must not flash in settled).
     */
    internal fun sinceImpact(seed: Int, i: Int): Float {
        if (seed != seedId) return -1f
        return launches.getOrNull(i)?.let { (now - it.impactMs).toFloat() } ?: -1f
    }

    internal suspend fun runClock() {
        while (true) withFrameMillis { clock.longValue = it }
    }

    internal fun onMarker(id: Int, count: Int, quiet: Boolean, frameMs: Long): List<Launch> {
        if (id != seedId) {
            seedId = id
            this.quiet = quiet
            launches.clear()
        }
        val from = orb?.takeIf { it.isAttached }?.let { it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
        val flight = if (quiet || from == null) 0 else COMET_MS
        val first = launches.size
        val added = (first until count).map { i ->
            // Parts that arrive together (a re-ground) fan out a beat apart; alternate arcs so comets don't overlap.
            Launch(frameMs + (i - first) * STAGGER_MS, flight, from, if (i % 2 == 0) 1f else -1f)
        }
        launches += added
        return added
    }

    internal fun reset() {
        seedId = -1
        launches.clear()
    }

    internal companion object {
        const val COMET_MS = 460
        const val STAGGER_MS = 110
    }
}

@Composable
fun rememberMarkerMotion(marker: State<MarkerState?>): MarkerMotion {
    val motion = remember { MarkerMotion() }
    val view = LocalView.current
    val active by remember { derivedStateOf { marker.value != null } }
    LaunchedEffect(active) {
        if (active) motion.runClock()
    }
    LaunchedEffect(motion) {
        snapshotFlow { marker.value?.let { Triple(it.seedId, it.targets.size, it.quiet) } }.collect { key ->
            if (key == null) {
                motion.reset()
                return@collect
            }
            val (id, count, quiet) = key
            // Stamp cues with a real frame time: the clock may only just be starting.
            val frameMs = withFrameMillis { it }
            val added = motion.onMarker(id, count, quiet, frameMs)
            if (quiet) return@collect
            added.forEach { l ->
                launch {
                    delay(l.impactMs - frameMs)
                    if (motion.seedId == id) view.impactTick(strong = motion.launches.firstOrNull() === l)
                }
            }
        }
    }
    return motion
}

/** Marks the composable (Fixy's orb) that comets launch from. */
fun Modifier.markerLaunchPad(motion: MarkerMotion): Modifier = onGloballyPositioned { motion.orb = it }

/** A crisp confirm on the first part locking on, a lighter tick for each part streamed after it. No permission needed. */
private fun View.impactTick(strong: Boolean) {
    val type = when {
        strong && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
        strong -> HapticFeedbackConstants.VIRTUAL_KEY
        else -> HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(type)
}

/**
 * The hand-off: a comet of Amber light from Fixy's orb to each part it points at, drawn over the cards. It arcs,
 * speeds up into the target, homes on the part's tracked position as the phone moves, sheds sparks and collapses
 * into the part on impact (where [MarkerOverlay] takes over with the lock-on).
 */
@Composable
fun HandoffLayer(marker: State<MarkerState?>, motion: MarkerMotion, modifier: Modifier = Modifier) {
    val origin = remember { arrayOf(Offset.Zero) }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { origin[0] = it.positionInRoot() }) {
        val m = marker.value ?: return@Canvas
        if (m.quiet || m.seedId != motion.seedId || m.targets.isEmpty()) return@Canvas
        val now = motion.now
        val toView = BoxMapper.fillCenter(m.frameWidth, m.frameHeight, size.width, size.height)
        motion.launches.forEachIndexed { i, l ->
            val from = (l.from ?: return@forEachIndexed) - origin[0]
            val t = now - l.atMs
            if (l.flightMs == 0 || t < 0 || t > l.flightMs + SPARK_LIFE_MS) return@forEachIndexed
            val target = m.targets.getOrNull(i) ?: return@forEachIndexed
            val v = toView.map(target.box)
            drawComet(from, Offset(v.centerX, v.centerY), t.toFloat(), l.flightMs.toFloat(), l.bend, seed = motion.seedId * 31 + i)
        }
    }
}

private fun DrawScope.drawComet(from: Offset, to: Offset, t: Float, flight: Float, bend: Float, seed: Int) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = hypot(dx, dy).coerceAtLeast(1f)
    // Bow the flight path sideways by a quarter of its length: a thrown arc, not a laser.
    val ctrl = Offset(from.x + dx / 2f - dy / dist * bend * dist * 0.28f, from.y + dy / 2f + dx / dist * bend * dist * 0.28f)
    fun at(u: Float): Offset {
        val a = 1f - u
        return Offset(a * a * from.x + 2f * a * u * ctrl.x + u * u * to.x, a * a * from.y + 2f * a * u * ctrl.y + u * u * to.y)
    }

    val flying = t < flight
    val head = if (flying) easeInSine(t / flight) else 1f
    // The tail stretches with speed, then snaps into the target once the head has landed.
    val tailCollapse = window(t, flight, TAIL_COLLAPSE_MS)
    val tailStart = lerp((head - TAIL_LEN * head.pow(0.5f)).coerceAtLeast(0f), 1f, easeOutCubic(tailCollapse))

    // Launch flare on the orb.
    val launchP = window(t, 0f, LAUNCH_FLARE_MS)
    if (launchP < 1f) {
        val e = easeOutCubic(launchP)
        drawCircle(Amber.copy(alpha = 0.35f * (1f - e)), lerp(10.dp.toPx(), 22.dp.toPx(), e), from)
        drawCircle(Amber.copy(alpha = 0.9f * (1f - e)), lerp(8.dp.toPx(), 40.dp.toPx(), e), from, style = Stroke(lerp(3.dp.toPx(), 0.5.dp.toPx(), e)))
    }

    // Tail: segments that thicken and brighten toward the head, in three passes (bloom, body, white-hot core).
    if (head - tailStart > 0.002f) {
        val n = TAIL_SEGMENTS
        for (pass in 0..2) {
            var prev = at(tailStart)
            for (k in 1..n) {
                val f = k / n.toFloat()
                val p = at(lerp(tailStart, head, f))
                val w = lerp(0.6.dp.toPx(), 5.dp.toPx(), f.pow(1.6f))
                when (pass) {
                    0 -> drawLine(Amber.copy(alpha = 0.16f * f * f), prev, p, w * 3.2f, StrokeCap.Round)
                    1 -> drawLine(Amber.copy(alpha = f * f), prev, p, w, StrokeCap.Round)
                    else -> if (f > 0.65f) drawLine(Paper.copy(alpha = (f - 0.65f) / 0.35f), prev, p, w * 0.38f, StrokeCap.Round)
                }
                prev = p
            }
        }
    }

    // Head: a star-like glow while in flight.
    if (flying) {
        val h = at(head)
        drawCircle(Amber.copy(alpha = 0.12f), 20.dp.toPx(), h)
        drawCircle(Amber.copy(alpha = 0.3f), 10.dp.toPx(), h)
        drawCircle(Amber, 5.dp.toPx(), h)
        drawCircle(Paper, 2.5.dp.toPx(), h)
    }

    // Sparks shed along the path: each drifts off with drag and a little gravity, cooling from white to amber.
    for (k in 0 until SPARKS) {
        val emitAt = flight * (0.18f + 0.78f * k / SPARKS)
        val age = t - emitAt
        if (age < 0f || age > SPARK_LIFE_MS) continue
        val life = age / SPARK_LIFE_MS
        val angle = hash01(seed * 97 + k) * 2f * PI.toFloat()
        val speed = lerp(50f, 170f, hash01(seed * 131 + k * 7)) * density / 1000f // px per ms
        val s = age * (1f - 0.5f * life)
        val p0 = at(easeInSine(emitAt / flight))
        val p = Offset(p0.x + cos(angle) * speed * s, p0.y + sin(angle) * speed * s + 0.00035f * density * age * age)
        val r = lerp(2.2.dp.toPx(), 0.3.dp.toPx(), life)
        drawCircle(Amber.copy(alpha = 0.35f * (1f - life)), r * 2.6f, p)
        drawCircle(if (life < 0.3f) Paper else Amber, r, p, alpha = 1f - life)
    }
}

private const val TAIL_LEN = 0.42f
private const val TAIL_SEGMENTS = 22
private const val TAIL_COLLAPSE_MS = 150f
private const val LAUNCH_FLARE_MS = 280f
private const val SPARKS = 8
private const val SPARK_LIFE_MS = 420
