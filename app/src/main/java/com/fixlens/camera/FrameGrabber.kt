package com.fixlens.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt

/** Keeps the latest camera frame so a question can grab it as the keyframe. Runs on the analysis thread. */
class FrameGrabber : ImageAnalysis.Analyzer {

    private val latest = AtomicReference<Pair<Bitmap, Int>?>(null)

    override fun analyze(image: ImageProxy) {
        latest.set(image.toBitmap() to image.imageInfo.rotationDegrees)
        image.close()
    }

    /** Forgets the last frame, so a new session never uses a picture from the previous one. */
    fun clear() = latest.set(null)

    /**
     * Saves the latest frame upright, scaled so the long side is [longSide] px and both sides are
     * multiples of 32 (MNN's Qwen3-VL resize is then a no-op). Returns null if no frame yet.
     */
    fun saveKeyframe(file: File, longSide: Int = 448): File? {
        val (bitmap, rotation) = latest.get() ?: return null
        val upright = if (rotation == 0) bitmap else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )
        val scale = longSide.toFloat() / max(upright.width, upright.height)
        fun align(v: Float) = max(32, (v / 32f).roundToInt() * 32)
        val scaled = Bitmap.createScaledBitmap(upright, align(upright.width * scale), align(upright.height * scale), true)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }
}
