package com.fixlens.ir

import android.util.Log
import com.fixlens.app.TAG
import com.fixlens.camera.FrameRingBuffer
import kotlinx.coroutines.delay
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

/**
 * Notices a TV or projector reacting to a code (a volume bar, a menu) from the camera, without the VLM: each
 * analysis frame is shrunk to a 24×24 grid of mean brightness; after a send, a few cells that clearly change
 * while the rest of the picture stays put means "the screen responded". A moving camera or a playing video
 * gives "unsure", and pairing asks instead. Frames arrive on the analysis thread; only while [active].
 */
class ScreenProbe {
    enum class Verdict { Changed, Same, Unsure }

    @Volatile var active = false
        set(value) {
            field = value
            if (!value) synchronized(lock) { recent.clear(); watching = null }
        }

    private val lock = Any()
    private val recent = ArrayDeque<FloatArray>()
    private var watching: MutableList<FloatArray>? = null
    private val small = Mat()

    /** Analysis thread (FrameGrabber → FlowTracker's frame callback). */
    fun onFrame(ring: FrameRingBuffer) {
        if (!active || ring.size == 0) return
        Imgproc.resize(ring.frame(0), small, Size(GRID.toDouble(), GRID.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        val bytes = ByteArray(GRID * GRID)
        small.get(0, 0, bytes)
        val grid = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF).toFloat() }
        synchronized(lock) {
            val w = watching
            if (w != null) {
                w += grid
            } else {
                recent.addLast(grid)
                while (recent.size > BEFORE_FRAMES) recent.removeFirst()
            }
        }
    }

    /** Call right after a send: watches for [ms] and says whether the screen reacted. */
    suspend fun watch(ms: Long = WATCH_MS): Verdict {
        val before: List<FloatArray>
        synchronized(lock) {
            before = recent.toList()
            watching = mutableListOf()
        }
        delay(ms)
        val after: List<FloatArray>
        synchronized(lock) {
            after = watching.orEmpty()
            watching = null
            recent.clear()
        }
        // The first frames can still predate the TV's reaction.
        val verdict = detect(before, after.drop(SKIP_FRAMES))
        Log.i(TAG, "Screen check: $verdict (${before.size} frames before, ${after.size} after)")
        return verdict
    }

    companion object {
        const val GRID = 24
        const val WATCH_MS = 1600L
        private const val BEFORE_FRAMES = 12
        private const val SKIP_FRAMES = 3
        /** A cell counts as changed past this many brightness levels (of 255), or 3× its own flicker. */
        private const val MIN_DELTA = 10f
        /** Average flicker above this: the camera is moving or the screen plays video, so no call is made. */
        private const val BUSY_NOISE = 6f
        /** More than this share of cells changed in a frame: the whole view moved, not an on-screen overlay. */
        private const val MOVED_SHARE = 0.35f
        private const val MIN_CELLS = 4
        private const val MIN_CHANGED_FRAMES = 3

        /** Pure: compares the frames after a send with the frames just before it. */
        fun detect(before: List<FloatArray>, after: List<FloatArray>): Verdict {
            if (before.size < 3 || after.size < 3) return Verdict.Unsure
            val n = before[0].size
            val ref = FloatArray(n) { i -> before.map { it[i] }.sorted()[before.size / 2] }
            val noise = FloatArray(n) { i -> before.maxOf { abs(it[i] - ref[i]) } }
            if (noise.average() > BUSY_NOISE) return Verdict.Unsure
            var moved = 0
            var changed = 0
            for (grid in after) {
                val deltas = FloatArray(n) { grid[it] - ref[it] }
                // Exposure shifts move every cell together: take the median shift out first.
                val shift = deltas.sorted()[n / 2]
                val count = (0 until n).count { abs(deltas[it] - shift) > max(MIN_DELTA, 3 * noise[it]) }
                when {
                    count > n * MOVED_SHARE -> moved++
                    count >= MIN_CELLS -> changed++
                }
            }
            return when {
                moved * 2 >= after.size -> Verdict.Unsure
                changed >= MIN_CHANGED_FRAMES -> Verdict.Changed
                else -> Verdict.Same
            }
        }
    }
}
