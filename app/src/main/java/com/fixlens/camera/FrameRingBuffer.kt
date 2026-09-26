package com.fixlens.camera

import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.abs

/**
 * The last few seconds of analysis frames (gray, upright, analysis space) with their sensor timestamps, so
 * the tracker can start on the keyframe and fast-forward to "now". Mats are allocated once and reused.
 * Analysis thread only.
 */
class FrameRingBuffer(private val capacity: Int) {

    private val frames = arrayOfNulls<Mat>(capacity)
    private val stamps = LongArray(capacity)
    private var newest = -1

    var size = 0
        private set
    var width = 0
        private set
    var height = 0
        private set

    /** Writes the next frame into a reused [width] × [height] 8-bit Mat via [fill], stamped [timestampNs]. */
    fun push(timestampNs: Long, width: Int, height: Int, fill: (Mat) -> Unit) {
        if (width != this.width || height != this.height) {
            frames.forEachIndexed { i, m -> m?.release(); frames[i] = null }
            this.width = width
            this.height = height
            clear()
        }
        val i = (newest + 1) % capacity
        val slot = frames[i] ?: Mat(height, width, CvType.CV_8UC1).also { frames[i] = it }
        fill(slot)
        stamps[i] = timestampNs
        newest = i
        if (size < capacity) size++
    }

    /** Frame [age] steps back (0 = newest). */
    fun frame(age: Int): Mat = frames[slot(age)]!!

    fun timestamp(age: Int): Long = stamps[slot(age)]

    /** Age of the frame closest to [timestampNs], or -1 if the buffer is empty or it's older than everything held. */
    fun ageOf(timestampNs: Long, toleranceNs: Long = 20_000_000L): Int {
        var best = -1
        var bestDiff = Long.MAX_VALUE
        for (age in 0 until size) {
            val diff = abs(timestamp(age) - timestampNs)
            if (diff < bestDiff) {
                best = age
                bestDiff = diff
            }
        }
        return if (best >= 0 && (bestDiff <= toleranceNs || timestampNs >= timestamp(0))) best else -1
    }

    fun clear() {
        size = 0
        newest = -1
    }

    private fun slot(age: Int): Int {
        require(age in 0 until size) { "age $age outside 0..${size - 1}" }
        return ((newest - age) % capacity + capacity) % capacity
    }
}
