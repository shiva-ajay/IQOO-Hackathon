package com.fixlens.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fixlens.app.TAG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The camera analysis stage (YUV_420_888 frames, on the analysis thread). For every frame it takes the Y
 * plane as a free grayscale image, crops it to what the preview shows ([ImageProxy.getCropRect], set by the
 * shared ViewPort), rotates it upright, downscales it to [ANALYSIS_LONG_SIDE] and pushes it into the ring
 * buffer. Then [onFrame] runs (the tracker). Only a requested keyframe is converted to RGB.
 * Coordinate spaces: docs/marker-tracking.md §1.
 */
class FrameGrabber(private val onFrame: (FrameRingBuffer) -> Unit) : ImageAnalysis.Analyzer {

    /** A saved keyframe. [timestampNs] is 0 for an image that didn't come from the camera (then there's nothing to track). */
    data class Keyframe(
        val file: File,
        val timestampNs: Long,
        val width: Int,
        val height: Int,
        val analysisWidth: Int,
        val analysisHeight: Int,
    )

    private class Request(val file: File, val longSide: Int) {
        val result = CompletableDeferred<Keyframe?>()
    }

    private val ring = FrameRingBuffer(RING_FRAMES)
    private val pending = ConcurrentLinkedQueue<Request>()
    private val clearRequested = AtomicBoolean(false)
    private var openCvReady: Boolean? = null
    private var scaled: Mat? = null

    private val _analysisSize = MutableStateFlow<android.util.Size?>(null)
    /** Size of analysis space (upright), once the first frame has arrived. */
    val analysisSize: StateFlow<android.util.Size?> = _analysisSize

    private var statFrames = 0
    private var statNs = 0L
    private var statMaxNs = 0L
    private var statStartNs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            if (!ensureOpenCv()) return
            if (clearRequested.getAndSet(false)) ring.clear()
            val t0 = System.nanoTime()
            pushGray(image)
            // Every waiting request (a question and a re-ground may overlap) gets this same frame.
            while (true) writeKeyframe(image, pending.poll() ?: break)
            onFrame(ring)
            logStats(System.nanoTime() - t0)
        } catch (e: Throwable) {
            Log.e(TAG, "Analysis frame failed", e)
        } finally {
            image.close()
        }
    }

    /**
     * Saves the next camera frame upright as a JPEG whose long side is [longSide] px and whose sides are
     * multiples of 32 (MNN's Qwen3-VL resize is then a no-op). Null if no frame arrives within a second.
     */
    suspend fun captureKeyframe(file: File, longSide: Int = KEYFRAME_LONG_SIDE): Keyframe? {
        val request = Request(file, longSide)
        pending.add(request)
        return withTimeoutOrNull(KEYFRAME_TIMEOUT_MS) { request.result.await() }
            .also { if (it == null) pending.remove(request) }
    }

    /** Forgets buffered frames, so a new session never uses a picture from the previous one. */
    fun clear() = clearRequested.set(true)

    private fun ensureOpenCv(): Boolean = openCvReady ?: OpenCVLoader.initLocal().also {
        openCvReady = it
        Log.i(TAG, "OpenCV ${OpenCVLoader.OPENCV_VERSION} ${if (it) "loaded" else "FAILED to load"}")
    }

    private fun pushGray(image: ImageProxy) {
        val plane = image.planes[0]
        val crop = image.cropRect
        val rotation = image.imageInfo.rotationDegrees
        val scale = ANALYSIS_LONG_SIDE.toFloat() / max(crop.width(), crop.height())
        val w = (crop.width() * scale).roundToInt()
        val h = (crop.height() * scale).roundToInt()
        val upright = rotation == 90 || rotation == 270
        val outW = if (upright) h else w
        val outH = if (upright) w else h
        // The Y plane as-is (no copy): pixel stride is 1 for Y, rows may be padded.
        val y = Mat(image.height, image.width, CvType.CV_8UC1, plane.buffer, plane.rowStride.toLong())
        val roi = y.submat(crop.top, crop.bottom, crop.left, crop.right)
        ring.push(image.imageInfo.timestamp, outW, outH) { slot ->
            if (rotation == 0) {
                Imgproc.resize(roi, slot, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                val tmp = scaled ?: Mat().also { scaled = it }
                Imgproc.resize(roi, tmp, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                Core.rotate(tmp, slot, rotateCode(rotation))
            }
        }
        roi.release()
        y.release()
        val size = _analysisSize.value
        if (size == null || size.width != outW || size.height != outH) {
            _analysisSize.value = android.util.Size(outW, outH)
            Log.i(TAG, "Analysis frames ${image.width}x${image.height} rot $rotation crop $crop -> analysis ${outW}x$outH")
        }
    }

    private fun writeKeyframe(image: ImageProxy, request: Request) {
        val t0 = System.nanoTime()
        val keyframe = runCatching {
            val full = image.toBitmap()
            val upright = uprightCrop(full, image.cropRect, image.imageInfo.rotationDegrees)
            val (kw, kh) = keyframeSize(upright.width, upright.height, request.longSide)
            val out = Bitmap.createScaledBitmap(upright, kw, kh, true)
            request.file.parentFile?.mkdirs()
            request.file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            Keyframe(request.file, image.imageInfo.timestamp, kw, kh, ring.width, ring.height)
        }.onFailure { Log.e(TAG, "Keyframe failed", it) }.getOrNull()
        Log.i(TAG, "Keyframe ${keyframe?.width}x${keyframe?.height} in ${(System.nanoTime() - t0) / 1_000_000} ms")
        request.result.complete(keyframe)
    }

    private fun logStats(ns: Long) {
        if (statFrames == 0) statStartNs = System.nanoTime()
        statFrames++
        statNs += ns
        statMaxNs = max(statMaxNs, ns)
        if (statFrames == STATS_EVERY) {
            val seconds = (System.nanoTime() - statStartNs) / 1e9
            Log.i(
                TAG,
                "Analysis: %.1f ms avg, %.1f ms max per frame (%.0f fps, %dx%d)".format(
                    statNs / 1e6 / statFrames, statMaxNs / 1e6, (statFrames - 1) / seconds, ring.width, ring.height,
                ),
            )
            statFrames = 0
            statNs = 0
            statMaxNs = 0
        }
    }

    companion object {
        /** Long side of analysis space (the tracker's working resolution). */
        const val ANALYSIS_LONG_SIDE = 480
        /** Long side of the keyframe sent to the VLM. */
        const val KEYFRAME_LONG_SIDE = 448
        /** ~6 s at 30 fps: covers the time from keyframe to box line, including a re-ground. */
        const val RING_FRAMES = 180
        private const val KEYFRAME_TIMEOUT_MS = 1000L
        private const val JPEG_QUALITY = 90
        private const val STATS_EVERY = 30

        private fun rotateCode(degrees: Int) = when (degrees) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            else -> Core.ROTATE_90_COUNTERCLOCKWISE
        }

        private fun uprightCrop(src: Bitmap, crop: Rect, rotation: Int): Bitmap {
            val matrix = Matrix().apply { if (rotation != 0) postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height(), matrix, true)
        }

        /** Keyframe size for an upright image: long side [longSide], both sides rounded to a multiple of 32. */
        fun keyframeSize(width: Int, height: Int, longSide: Int): Pair<Int, Int> {
            val scale = longSide.toFloat() / max(width, height)
            fun align(v: Float) = max(32, (v / 32f).roundToInt() * 32)
            return align(width * scale) to align(height * scale)
        }

        /** Debug: makes a keyframe from an image file instead of the camera (nothing to track, so no timestamp). */
        fun keyframeFromFile(src: File, dst: File, longSide: Int = KEYFRAME_LONG_SIDE): Keyframe? {
            val bitmap = BitmapFactory.decodeFile(src.absolutePath) ?: return null
            val (kw, kh) = keyframeSize(bitmap.width, bitmap.height, longSide)
            dst.parentFile?.mkdirs()
            dst.outputStream().use { Bitmap.createScaledBitmap(bitmap, kw, kh, true).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            return Keyframe(dst, 0L, kw, kh, 0, 0)
        }
    }
}
