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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
 * Fixy's pointers over the camera preview: a pulsing rounded box on each tracked part (a numbered ring on each
 * small part like a screw), the rest of the view dimmed, and a chip per kind of part. It holds while tracking
 * wavers and fades once lost.
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
    // Offscreen, so the holes cut out of the dim layer (one per part) can overlap without cancelling out.
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val m = marker.value ?: return@Canvas
        if (m.targets.isEmpty()) return@Canvas
        val toView = BoxMapper.fillCenter(m.frameWidth, m.frameHeight, size.width, size.height)
        // Lock-on: markers close in from 1.4x and fade up when a new answer arrives.
        val lock = lockOn.value
        val grow = 1f + 0.4f * (1f - lock)
        val a = alpha * (0.35f + 0.65f * lock)
        val holding = m.status == MarkerState.Status.Holding
        val dash = if (holding) PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 8.dp.toPx())) else null
        val pad = 6.dp.toPx()
        val ringRadius = POINT_RING.toPx() * grow
        val shapes = m.targets.map { t ->
            val v = toView.map(t.box)
            if (t.isPoint) Rect(Offset(v.centerX, v.centerY), ringRadius)
            else Rect(v.left - pad, v.top - pad, v.right + pad, v.bottom + pad).scaleAround(grow)
        }

        // Dim everything outside the parts.
        drawRect(Ink.copy(alpha = 0.42f * a))
        m.targets.forEachIndexed { i, t ->
            val r = shapes[i]
            if (t.isPoint) drawCircle(Color.Black, r.width / 2f + 6.dp.toPx(), r.center, blendMode = BlendMode.DstOut)
            else drawRoundRect(Color.Black, r.topLeft, r.size, cornerRadius(r), blendMode = BlendMode.DstOut)
        }

        val numbered = m.targets.count { it.isPoint } > 1
        var pointNo = 0
        m.targets.forEachIndexed { i, t ->
            val r = shapes[i]
            val breathe = 10.dp.toPx() * pulse
            val ringColor = Amber.copy(alpha = 0.55f * (1f - pulse) * a)
            val color = Amber.copy(alpha = a * (if (holding) 0.75f else 1f))
            if (t.isPoint) {
                // A screw-sized ring with a dot, numbered when there are several.
                drawCircle(ringColor, r.width / 2f + breathe, r.center, style = Stroke(2.dp.toPx()))
                drawCircle(color, r.width / 2f, r.center, style = Stroke(3.dp.toPx(), pathEffect = dash))
                drawCircle(color, 3.dp.toPx(), r.center)
                pointNo++
                if (numbered) drawBadge(textMeasurer, pointNo.toString(), Offset(r.right, r.top), a)
            } else {
                val ring = r.inflate(breathe)
                drawRoundRect(ringColor, ring.topLeft, ring.size, CornerRadius(cornerRadius(r).x + breathe), style = Stroke(2.dp.toPx()))
                drawRoundRect(color, r.topLeft, r.size, cornerRadius(r), style = Stroke(3.dp.toPx(), pathEffect = dash))
            }
        }

        // One chip per kind of part: "Screw ×8" above the topmost of them, or the part's name above it.
        m.targets.indices.groupBy { m.targets[it].label }.entries.take(MAX_CHIPS).forEach { (label, idx) ->
            val top = idx.minBy { shapes[it].top }
            val name = label.replaceFirstChar { it.uppercase() }
            drawLabel(textMeasurer, if (idx.size > 1) "$name ×${idx.size}" else name, shapes[top], a)
        }
    }
}

private fun DrawScope.cornerRadius(r: Rect) = CornerRadius(minOf(16.dp.toPx(), r.minDimension / 3f))

/** A small numbered dot at the top-right of a point marker. */
private fun DrawScope.drawBadge(textMeasurer: TextMeasurer, text: String, at: Offset, alpha: Float) {
    val layout = textMeasurer.measure(text, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink))
    val r = maxOf(layout.size.width, layout.size.height) / 2f + 3.dp.toPx()
    drawCircle(Amber.copy(alpha = alpha), r, at)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f), alpha = alpha)
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
    if (f.raw.isEmpty()) {
        debugText(textMeasurer, "no box: ${f.note}", Offset(area.left + 12f, area.top + 12f), Amber)
        return
    }
    for (raw in f.raw) {
        for ((scale, color) in listOf(CoordScale.NORMALIZED_1000 to Amber, CoordScale.ABSOLUTE_PIXELS to DebugCyan)) {
            val kb = BoxMapper.modelToKeyframe(raw, scale, f.width, f.height) ?: continue
            val v = toView(kb)
            drawRect(color, Offset(v.left, v.top), Size(v.width, v.height), style = Stroke(3.dp.toPx()))
        }
    }
    val first = f.raw.first()
    debugText(
        textMeasurer,
        "${f.note}; first [${first.x1.toInt()},${first.y1.toInt()},${first.x2.toInt()},${first.y2.toInt()}] kf ${f.width}x${f.height}  amber=0..1000 cyan=px",
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
/** Radius of the ring drawn on a point target (a screw). */
private val POINT_RING = 16.dp
/** Label chips drawn at most (one per kind of part). */
private const val MAX_CHIPS = 4
