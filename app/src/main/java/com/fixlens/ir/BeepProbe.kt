package com.fixlens.ir

import android.util.Log
import com.fixlens.app.TAG
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos

/**
 * Listens for the short beep an AC makes when it accepts a remote code, so pairing can move on without asking.
 * Fed mic blocks by SpeechInput.monitor (audio thread). [listen] compares ~1.5 s after a send with the room
 * just before it: a beep is a loud, nearly pure tone between 1.5 and 5 kHz lasting at least ~60 ms.
 * Nothing is stored or transcribed. If the phone's voice processing filters the tone out, it just never fires
 * and the user answers instead.
 */
class BeepProbe {
    private val lock = Any()
    private val before = FloatArray(BEFORE)
    private var beforePos = 0
    private var beforeFull = false
    private var window: FloatArray? = null
    private var windowSize = 0

    /** Audio thread: keeps the last ~0.5 s and, while listening, fills the window. */
    fun feed(samples: FloatArray) = synchronized(lock) {
        val w = window
        if (w != null) {
            val n = minOf(samples.size, w.size - windowSize)
            System.arraycopy(samples, 0, w, windowSize, n)
            windowSize += n
            return@synchronized
        }
        for (v in samples) {
            before[beforePos] = v
            beforePos = (beforePos + 1) % BEFORE
            if (beforePos == 0) beforeFull = true
        }
    }

    /** Listens for [ms] (call right after sending) and says whether a beep was heard. */
    suspend fun listen(ms: Long = LISTEN_MS): Boolean {
        val baseline: FloatArray
        synchronized(lock) {
            baseline = if (beforeFull) before.copyOfRange(beforePos, BEFORE) + before.copyOfRange(0, beforePos)
            else before.copyOf(beforePos)
            window = FloatArray((SAMPLE_RATE * ms / 1000).toInt())
            windowSize = 0
        }
        delay(ms)
        val heard: FloatArray
        synchronized(lock) {
            heard = window!!.copyOf(windowSize)
            window = null
            beforePos = 0
            beforeFull = false
        }
        if (heard.size < FRAME * 2) {
            Log.i(TAG, "Beep check: no audio (mic closed?)")
            return false
        }
        val result = detect(baseline, heard)
        Log.i(TAG, "Beep check: ${if (result) "beep" else "no beep"} (${heard.size * 1000 / SAMPLE_RATE} ms heard)")
        return result
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME = 512
        private const val BEFORE = SAMPLE_RATE / 2
        const val LISTEN_MS = 1500L
        /** Goertzel bins k·fs/N between 1.5 and 5 kHz (31.25 Hz apart, so any tone lands within half a bin). */
        private val BINS = (48..160).toList()
        private val COEFFS = BINS.map { k -> 2.0 * cos(2.0 * PI * k / FRAME) }.toDoubleArray()

        /**
         * Pure: true if [heard] holds at least two back-to-back frames that are both tonal (one bin carries
         * over a quarter of the frame's energy) and clearly louder than the room in [baseline], at the same pitch.
         */
        fun detect(baseline: FloatArray, heard: FloatArray): Boolean {
            val room = frameEnergies(baseline).sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
            val floor = maxOf(room * LOUDER_THAN_ROOM, MIN_ENERGY)
            var run = 0
            var lastBin = -100
            var i = 0
            while (i + FRAME <= heard.size) {
                val (energy, tone) = analyse(heard, i)
                val (ratio, bin) = tone
                if (energy > floor && ratio > TONAL && kotlin.math.abs(bin - lastBin) <= 3) {
                    run++
                    if (run >= 2) return true
                } else {
                    run = if (energy > floor && ratio > TONAL) 1 else 0
                }
                lastBin = if (energy > floor && ratio > TONAL) bin else -100
                i += FRAME
            }
            return false
        }

        private fun frameEnergies(x: FloatArray): List<Double> =
            (0 until x.size / FRAME).map { f -> var e = 0.0; for (j in 0 until FRAME) e += x[f * FRAME + j] * x[f * FRAME + j]; e }

        /** Frame energy, and the strongest bin's share of it with its index. */
        private fun analyse(x: FloatArray, from: Int): Pair<Double, Pair<Double, Int>> {
            var energy = 0.0
            for (j in 0 until FRAME) energy += x[from + j] * x[from + j]
            if (energy <= 0.0) return 0.0 to (0.0 to -1)
            var best = 0.0
            var bestBin = -1
            for (b in BINS.indices) {
                val c = COEFFS[b]
                var s1 = 0.0
                var s2 = 0.0
                for (j in 0 until FRAME) {
                    val s0 = x[from + j] + c * s1 - s2
                    s2 = s1
                    s1 = s0
                }
                // |X|² scaled so a pure tone on a bin gives its frame energy.
                val power = (s1 * s1 + s2 * s2 - c * s1 * s2) * 2.0 / FRAME
                if (power > best) {
                    best = power
                    bestBin = BINS[b]
                }
            }
            return energy to ((best / energy) to bestBin)
        }

        private const val TONAL = 0.25
        private const val LOUDER_THAN_ROOM = 4.0
        /** About -50 dBFS over a frame: quieter than this is never a beep. */
        private const val MIN_ENERGY = FRAME * 1e-5
    }
}
