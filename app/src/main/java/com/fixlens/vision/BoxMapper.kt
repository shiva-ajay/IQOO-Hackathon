package com.fixlens.vision

import kotlin.math.max
import kotlin.math.min

/** An axis-aligned box in pixels of some image (which image depends on where it's used, see docs/marker-tracking.md). */
data class PxBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun scale(sx: Float, sy: Float) = PxBox(left * sx, top * sy, right * sx, bottom * sy)

    /** Grows (or shrinks) the box around its centre by [factor]. */
    fun expand(factor: Float): PxBox {
        val hw = width * factor / 2f
        val hh = height * factor / 2f
        return PxBox(centerX - hw, centerY - hh, centerX + hw, centerY + hh)
    }

    fun clampTo(w: Float, h: Float) =
        PxBox(left.coerceIn(0f, w), top.coerceIn(0f, h), right.coerceIn(0f, w), bottom.coerceIn(0f, h))

    companion object {
        fun centered(cx: Float, cy: Float, w: Float, h: Float) = PxBox(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}

/** A box as the model wrote it, in the model's coordinate scale. A point answer has x1 == x2 and y1 == y2. */
data class ModelBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val label: String? = null,
    val isPoint: Boolean = false,
)

/** How the VLM expresses coordinates. Qwen3-VL: 0..1000 of the image. Qwen2.5-VL: pixels of the resized input. */
enum class CoordScale { NORMALIZED_1000, ABSOLUTE_PIXELS }

/** A uniform scale plus offset, e.g. analysis space → view space. */
data class ScaleOffset(val scale: Float, val dx: Float, val dy: Float) {
    fun mapX(x: Float) = x * scale + dx
    fun mapY(y: Float) = y * scale + dy
    fun map(b: PxBox) = PxBox(mapX(b.left), mapY(b.top), mapX(b.right), mapY(b.bottom))
}

/** Model coords → keyframe px → analysis px → view px (see docs/marker-tracking.md §1). Pure math. */
object BoxMapper {

    /** Smallest box side we accept, in keyframe px; smaller answers are noise. */
    const val MIN_SIDE_PX = 3f

    /** Side of the box drawn around a point answer, as a fraction of the keyframe's short side. */
    const val POINT_BOX_FRACTION = 0.12f

    /**
     * Maps a model box onto the keyframe (px, [kfWidth] × [kfHeight]). Swapped corners are fixed and the
     * box is clamped to the image. Returns null for a degenerate box (outside the image, or thinner than
     * [MIN_SIDE_PX] on either side).
     */
    fun modelToKeyframe(box: ModelBox, scale: CoordScale, kfWidth: Int, kfHeight: Int): PxBox? {
        if (kfWidth <= 0 || kfHeight <= 0) return null
        val values = floatArrayOf(box.x1, box.y1, box.x2, box.y2)
        if (values.any { it.isNaN() || it.isInfinite() }) return null
        val sx = if (scale == CoordScale.NORMALIZED_1000) kfWidth / 1000f else 1f
        val sy = if (scale == CoordScale.NORMALIZED_1000) kfHeight / 1000f else 1f
        val w = kfWidth.toFloat()
        val h = kfHeight.toFloat()
        if (box.isPoint) {
            val x = box.x1 * sx
            val y = box.y1 * sy
            if (x !in 0f..w || y !in 0f..h) return null
            val side = min(w, h) * POINT_BOX_FRACTION
            return PxBox.centered(x, y, side, side).clampTo(w, h)
        }
        val raw = PxBox(min(box.x1, box.x2) * sx, min(box.y1, box.y2) * sy, max(box.x1, box.x2) * sx, max(box.y1, box.y2) * sy)
        val clamped = raw.clampTo(w, h)
        if (clamped.width < MIN_SIDE_PX || clamped.height < MIN_SIDE_PX) return null
        return clamped
    }

    /** Keyframe px → analysis px. Both are resizes of the same upright crop, so this is a per-axis scale. */
    fun keyframeToAnalysis(box: PxBox, kfWidth: Int, kfHeight: Int, analysisWidth: Int, analysisHeight: Int): PxBox =
        box.scale(analysisWidth.toFloat() / kfWidth, analysisHeight.toFloat() / kfHeight)

    /**
     * How an image of [srcWidth] × [srcHeight] lands in a view of [dstWidth] × [dstHeight] under `FILL_CENTER`:
     * scaled uniformly to cover the view, centred, overflow cropped.
     */
    fun fillCenter(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): ScaleOffset {
        val scale = max(dstWidth / srcWidth, dstHeight / srcHeight)
        return ScaleOffset(scale, (dstWidth - srcWidth * scale) / 2f, (dstHeight - srcHeight * scale) / 2f)
    }

    /** Like [fillCenter] but the whole image stays visible (letterboxed). */
    fun fitCenter(srcWidth: Int, srcHeight: Int, dstWidth: Float, dstHeight: Float): ScaleOffset {
        val scale = min(dstWidth / srcWidth, dstHeight / srcHeight)
        return ScaleOffset(scale, (dstWidth - srcWidth * scale) / 2f, (dstHeight - srcHeight * scale) / 2f)
    }
}
