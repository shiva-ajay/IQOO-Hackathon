package com.fixlens.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.app.FrozenKeyframe
import com.fixlens.tracking.MarkerState
import com.fixlens.vision.BoxMapper
import com.fixlens.vision.CoordScale
import com.fixlens.vision.PxBox
import com.fixlens.vision.ScaleOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DebugCyan = Color(0xFF3DD6F5)
private val DebugMagenta = Color(0xFFFF4FD8)

/**
 * Fixy's pointer over the camera preview: a pulsing rounded box on the tracked part, the rest of the view
 * dimmed, and a chip with the part's name. It holds while tracking wavers and fades once lost.
 *
 * [marker] is read only while drawing, so the ~30 updates a second redraw the canvas without recomposing.
 * Boxes arrive in analysis space and are mapped here (analysis → view is FILL_CENTER, docs/marker-tracking.md).
 */
@Composable
fun MarkerOverlay(
    marker: State<MarkerState?>,
    analysisSize: android.util.Size?,
    debugTestBox: Boolean,
    frozen: FrozenKeyframe?,
    modifier: Modifier = Modifier,
) {
    val seedId by remember { derivedStateOf { marker.value?.seedId } }
    val shown by remember { derivedStateOf { marker.value.let { it != null && it.status != MarkerState.Status.Lost } } }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(if (shown) 180 else FADE_OUT_MS, easing = LinearEasing),
        label = "markerAlpha",
    )
    val lockOn = remember { Animatable(1f) }
    LaunchedEffect(seedId) {
        if (seedId != null) {
            lockOn.snapTo(0f)
            lockOn.animateTo(1f, tween(LOCK_ON_MS, easing = FastOutSlowInEasing))
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val frozenImage = rememberKeyframe(frozen?.path)

    if (alpha > 0f) MarkerCanvas(marker, alpha, lockOn, textMeasurer, modifier)
    if (debugTestBox || frozen != null) {
        Canvas(modifier.fillMaxSize()) {
            if (frozen != null) drawFrozen(frozen, frozenImage, textMeasurer)
            if (debugTestBox && analysisSize != null) drawTestBox(analysisSize, textMeasurer)
        }
    }
}

@Composable
private fun MarkerCanvas(
    marker: State<MarkerState?>,
    alpha: Float,
    lockOn: Animatable<Float, *>,
    textMeasurer: TextMeasurer,
    modifier: Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "pulsePhase",
    )
    Canvas(modifier.fillMaxSize()) {
        val m = marker.value ?: return@Canvas
        val toView = BoxMapper.fillCenter(m.frameWidth, m.frameHeight, size.width, size.height)
        val pad = 6.dp.toPx()
        val tracked = toView.map(m.box).let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        // Lock-on: the box closes in from 1.4x and fades up when a new box arrives.
        val lock = lockOn.value
        val rect = tracked.scaleAround(1f + 0.4f * (1f - lock))
        val a = alpha * (0.35f + 0.65f * lock)
        val holding = m.status == MarkerState.Status.Holding
        val radius = CornerRadius(minOf(16.dp.toPx(), rect.minDimension / 3f))

        // Dim everything outside the part.
        val mask = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(rect, radius))
        }
        drawPath(mask, Ink.copy(alpha = 0.42f * a))

        // A soft ring breathing outwards.
        val ring = rect.inflate(10.dp.toPx() * pulse)
        drawRoundRect(
            color = Amber.copy(alpha = 0.55f * (1f - pulse) * a),
            topLeft = ring.topLeft, size = ring.size,
            cornerRadius = CornerRadius(radius.x + 10.dp.toPx() * pulse),
            style = Stroke(2.dp.toPx()),
        )
        // The marker itself; dashed while holding a wobbly track.
        drawRoundRect(
            color = Amber.copy(alpha = a * (if (holding) 0.75f else 1f)),
            topLeft = rect.topLeft, size = rect.size, cornerRadius = radius,
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = if (holding) PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 8.dp.toPx())) else null,
            ),
        )
        drawLabel(textMeasurer, m.label.replaceFirstChar { it.uppercase() }, rect, a)
    }
}

/** The part's name in a small chip above the box (below it when there's no room above). */
private fun DrawScope.drawLabel(textMeasurer: TextMeasurer, text: String, box: Rect, alpha: Float) {
    val layout = textMeasurer.measure(
        text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Paper),
        maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (size.width * 0.8f).toInt()),
    )
    val dot = 6.dp.toPx()
    val padH = 12.dp.toPx()
    val padV = 6.dp.toPx()
    val gap = 10.dp.toPx()
    val w = padH * 2 + dot + 8.dp.toPx() + layout.size.width
    val h = padV * 2 + layout.size.height
    val above = box.top - gap - h
    val top = if (above > 72.dp.toPx()) above else box.bottom + gap
    val left = (box.center.x - w / 2f).coerceIn(8.dp.toPx(), size.width - w - 8.dp.toPx())
    drawRoundRect(Ink.copy(alpha = 0.85f * alpha), Offset(left, top), Size(w, h), CornerRadius(h / 2f))
    drawCircle(Amber.copy(alpha = alpha), dot / 2f, Offset(left + padH + dot / 2f, top + h / 2f))
    drawText(layout, topLeft = Offset(left + padH + dot + 8.dp.toPx(), top + padV), alpha = alpha)
}

/** Debug: the analysis-space rect (25%,25%)–(75%,75%). It must sit exactly centred at half the view's size. */
private fun DrawScope.drawTestBox(analysis: android.util.Size, textMeasurer: TextMeasurer) {
    val t = BoxMapper.fillCenter(analysis.width, analysis.height, size.width, size.height)
    val box = t.map(PxBox(analysis.width * 0.25f, analysis.height * 0.25f, analysis.width * 0.75f, analysis.height * 0.75f))
    drawRect(DebugMagenta, Offset(box.left, box.top), Size(box.width, box.height), style = Stroke(3.dp.toPx()))
    // Reference cross at the view's true centre and quarter marks on the edges.
    val c = Offset(size.width / 2f, size.height / 2f)
    drawLine(Paper, c - Offset(20f, 0f), c + Offset(20f, 0f), 2f)
    drawLine(Paper, c - Offset(0f, 20f), c + Offset(0f, 20f), 2f)
    for (f in listOf(0.25f, 0.75f)) {
        drawLine(Paper, Offset(size.width * f, 0f), Offset(size.width * f, 40f), 3f)
        drawLine(Paper, Offset(0f, size.height * f), Offset(40f, size.height * f), 3f)
    }
    debugText(textMeasurer, "TEST ${analysis.width}x${analysis.height} (25–75%)", Offset(box.left + 8f, box.top + 8f), DebugMagenta)
}

/**
 * Debug: the question's keyframe at 50% over the live preview (it must line up when the phone is still),
 * with the raw VLM box read as 0..1000 (amber) and as pixels (cyan). A file keyframe is shown whole and opaque.
 */
private fun DrawScope.drawFrozen(f: FrozenKeyframe, image: ImageBitmap?, textMeasurer: TextMeasurer) {
    val fromCamera = f.analysisWidth > 0
    // Keyframe px → view px: through analysis space for a camera frame, letterboxed for a file.
    val toView: (PxBox) -> PxBox = if (fromCamera) {
        val t = BoxMapper.fillCenter(f.analysisWidth, f.analysisHeight, size.width, size.height);
        { b -> t.map(BoxMapper.keyframeToAnalysis(b, f.width, f.height, f.analysisWidth, f.analysisHeight)) }
    } else {
        val t: ScaleOffset = BoxMapper.fitCenter(f.width, f.height, size.width, size.height);
        { b -> t.map(b) }
    }
    val area = toView(PxBox(0f, 0f, f.width.toFloat(), f.height.toFloat()))
    if (!fromCamera) drawRect(Ink, Offset.Zero, size)
    if (image != null) {
        drawImage(
            image,
            dstOffset = IntOffset(area.left.toInt(), area.top.toInt()),
            dstSize = IntSize(area.width.toInt(), area.height.toInt()),
            alpha = if (fromCamera) 0.5f else 1f,
        )
    }
    val raw = f.raw ?: run {
        debugText(textMeasurer, "no box: ${f.note}", Offset(area.left + 12f, area.top + 12f), Amber)
        return
    }
    for ((scale, color) in listOf(CoordScale.NORMALIZED_1000 to Amber, CoordScale.ABSOLUTE_PIXELS to DebugCyan)) {
        val kb = BoxMapper.modelToKeyframe(raw, scale, f.width, f.height) ?: continue
        val v = toView(kb)
        drawRect(color, Offset(v.left, v.top), Size(v.width, v.height), style = Stroke(3.dp.toPx()))
    }
    debugText(
        textMeasurer,
        "raw [${raw.x1.toInt()},${raw.y1.toInt()},${raw.x2.toInt()},${raw.y2.toInt()}] kf ${f.width}x${f.height}  amber=0..1000 cyan=px",
        Offset(area.left + 12f, area.top + 12f),
        Paper,
    )
}

private fun DrawScope.debugText(textMeasurer: TextMeasurer, text: String, at: Offset, color: Color) {
    val layout = textMeasurer.measure(text, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color))
    drawRect(Ink.copy(alpha = 0.7f), at - Offset(4f, 2f), Size(layout.size.width + 8f, layout.size.height + 4f))
    drawText(layout, topLeft = at)
}

@Composable
private fun rememberKeyframe(path: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    return image
}

private fun Rect.scaleAround(f: Float): Rect {
    val hw = width * f / 2f
    val hh = height * f / 2f
    return Rect(center.x - hw, center.y - hh, center.x + hw, center.y + hh)
}

private const val FADE_OUT_MS = 350
private const val LOCK_ON_MS = 420
private const val PULSE_MS = 1400
