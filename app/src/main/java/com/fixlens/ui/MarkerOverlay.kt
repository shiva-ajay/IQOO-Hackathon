package com.fixlens.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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
import com.fixlens.ui.fx.easeInOutCubic
import com.fixlens.ui.fx.easeOutBack
import com.fixlens.ui.fx.easeOutCubic
import com.fixlens.ui.fx.lerp
import com.fixlens.ui.fx.springOut
import com.fixlens.ui.fx.window
import com.fixlens.vision.BoxMapper
import com.fixlens.vision.CoordScale
import com.fixlens.vision.PxBox
import com.fixlens.vision.ScaleOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

private val DebugCyan = Color(0xFF3DD6F5)
private val DebugMagenta = Color(0xFFFF4FD8)

/**
 * Fixy's pointers over the camera preview: each tracked part gets a lock-on (see [MarkerMotion] for the cues),
 * then a reticle of corner brackets over a thin outline that breathes, with the rest of the view dimmed and a
 * chip per kind of part. A small part like a screw gets a ring with converging ticks instead, numbered when
 * there are several. It holds while tracking wavers and fades once lost.
 *
 * [marker] and the motion clock are read only while drawing, so tracking updates and animation frames redraw the
 * canvas without recomposing. Boxes arrive in analysis space and are mapped here (analysis → view is FILL_CENTER,
 * docs/marker-tracking.md).
 */
@Composable
fun MarkerOverlay(
    marker: State<MarkerState?>,
    motion: MarkerMotion,
    analysisSize: android.util.Size?,
    debugTestBox: Boolean,
    frozen: FrozenKeyframe?,
    modifier: Modifier = Modifier,
) {
    val shown by remember { derivedStateOf { marker.value.let { it != null && it.status != MarkerState.Status.Lost } } }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(if (shown) 180 else FADE_OUT_MS, easing = LinearEasing),
        label = "markerAlpha",
    )
    val textMeasurer = rememberTextMeasurer()
    val frozenImage = rememberKeyframe(frozen?.path)

    if (alpha > 0f) MarkerCanvas(marker, motion, alpha, textMeasurer, modifier)
    if (debugTestBox || frozen != null) {
        Canvas(modifier.fillMaxSize()) {
            if (frozen != null) drawFrozen(frozen, frozenImage, textMeasurer)
            if (debugTestBox && analysisSize != null) drawTestBox(analysisSize, textMeasurer)
        }
    }
}

/** Paths reused every frame, so drawing allocates nothing per part (HWUI copies a path when it records it). */
private class MarkerPaths {
    val outline = Path()
    val segment = Path()
    val brackets = Path()
    val clip = Path()
    val measure = PathMeasure()
}

@Composable
private fun MarkerCanvas(
    marker: State<MarkerState?>,
    motion: MarkerMotion,
    alpha: Float,
    textMeasurer: TextMeasurer,
    modifier: Modifier,
) {
    val paths = remember { MarkerPaths() }
    // Offscreen, so the holes cut out of the dim layer (one per part) can overlap without cancelling out.
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val m = marker.value ?: return@Canvas
        if (m.targets.isEmpty()) return@Canvas
        val toView = BoxMapper.fillCenter(m.frameWidth, m.frameHeight, size.width, size.height)
        val holding = m.status == MarkerState.Status.Holding
        val quiet = m.quiet
        val a = alpha * (if (holding) 0.75f else 1f)
        val pad = 6.dp.toPx()
        val ringRadius = POINT_RING.toPx()
        val shapes = m.targets.map { t ->
            val v = toView.map(t.box)
            if (t.isPoint) Rect(Offset(v.centerX, v.centerY), ringRadius)
            else Rect(v.left - pad, v.top - pad, v.right + pad, v.bottom + pad)
        }
        val since = FloatArray(m.targets.size) { motion.sinceImpact(m.seedId, it) }

        // Dim everything outside the parts. On a new answer the dim closes in on the first part like an iris
        // (a giant circle shrinking onto its box); every later part opens its own hole where its comet lands.
        val iris = if (quiet) 1f else easeOutCubic(window(since[0], 0f, IRIS_MS))
        if (iris > 0f) {
            drawRect(Ink.copy(alpha = 0.42f * alpha * iris))
            val reach = hypot(size.width, size.height)
            m.targets.forEachIndexed { i, t ->
                if (since[i] < 0f) return@forEachIndexed
                val r = shapes[i]
                val hole = if (i == 0 && !quiet) {
                    val start = Rect(r.center, reach)
                    Rect(lerp(start.left, r.left, iris), lerp(start.top, r.top, iris), lerp(start.right, r.right, iris), lerp(start.bottom, r.bottom, iris))
                } else {
                    r.scaleAround(if (quiet) 1f else springOut(since[i] / 1000f, 0.6f, 18f))
                }
                val corner = if (t.isPoint) hole.minDimension / 2f + 6.dp.toPx() else lerp(reach, cornerRadius(r).x, if (i == 0) iris else 1f)
                if (t.isPoint) drawCircle(Color.Black, hole.width / 2f + 6.dp.toPx(), hole.center, blendMode = BlendMode.DstOut)
                else drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(minOf(corner, hole.minDimension / 2f)), blendMode = BlendMode.DstOut)
            }
        }

        val now = motion.now
        val numbered = m.targets.count { it.isPoint } > 1
        var pointNo = 0
        m.targets.forEachIndexed { i, t ->
            val s = since[i]
            if (s < 0f) return@forEachIndexed // its comet is still in flight
            val r = shapes[i]
            if (!quiet) drawImpact(r, s, a)
            if (t.isPoint) {
                drawPointLock(r, s, now, i, quiet, holding, a)
                pointNo++
                if (numbered && s > BADGE_AT_MS) drawBadge(textMeasurer, pointNo.toString(), Offset(r.right, r.top), a * window(s, BADGE_AT_MS, 160f))
            } else {
                drawBoxLock(paths, r, s, now, i, quiet, holding, a)
            }
        }

        // One chip per kind of part: "Screw ×8" above the topmost of them, or the part's name above it. It pops
        // in once the part's scan has passed.
        m.targets.indices.groupBy { m.targets[it].label }.entries.take(MAX_CHIPS).forEach { (label, idx) ->
            val top = idx.minBy { shapes[it].top }
            val p = if (quiet) 1f else window(since[top], CHIP_AT_MS, CHIP_MS)
            if (p <= 0f) return@forEach
            val name = label.replaceFirstChar { it.uppercase() }
            drawLabel(textMeasurer, if (idx.size > 1) "$name ×${idx.size}" else name, shapes[top], a * minOf(1f, p * 2f), lerp(0.55f, 1f, easeOutBack(p)))
        }
    }
}

/** Where a comet lands: a hot flash that cools to amber and two shockwave rings racing outwards. */
private fun DrawScope.drawImpact(r: Rect, since: Float, a: Float) {
    val flash = window(since, 0f, FLASH_MS)
    if (flash < 1f) {
        val fade = (1f - flash).pow(2)
        val radius = lerp(8.dp.toPx(), r.minDimension * 0.55f + 18.dp.toPx(), easeOutCubic(flash))
        drawCircle(Amber.copy(alpha = 0.32f * fade * a), radius * 1.5f, r.center)
        drawCircle(Paper.copy(alpha = 0.55f * fade * a), radius * 0.6f, r.center)
    }
    val reach = hypot(r.width, r.height) * 0.75f + 48.dp.toPx()
    for ((delayMs, strength) in SHOCKWAVES) {
        val p = window(since, delayMs, SHOCK_MS)
        if (p <= 0f || p >= 1f) continue
        val e = easeOutCubic(p)
        drawCircle(
            Amber.copy(alpha = strength * (1f - p) * a),
            lerp(r.minDimension * 0.3f, reach, e),
            r.center,
            style = Stroke(lerp(4.dp.toPx(), 0.5.dp.toPx(), e)),
        )
    }
}

/**
 * A part's box: corner brackets spring in from far out with a twist and a squeeze, an outline traces itself round
 * from two sparks, a scan line sweeps down through the part, then it settles into a breathing ring with a glint
 * that circles the outline now and then. A quiet re-lock is just a quick bracket snap and a light scan.
 */
private fun DrawScope.drawBoxLock(paths: MarkerPaths, r: Rect, since: Float, now: Long, i: Int, quiet: Boolean, holding: Boolean, a: Float) {
    val corner = cornerRadius(r)
    val sec = since / 1000f
    val snap = if (quiet) springOut(sec, 0.6f, 24f) else springOut(sec, 0.55f, 15f)
    val scale = lerp(if (quiet) 1.25f else 1.9f, 1f, snap)
    val twist = if (quiet) 0f else lerp(-14f, 0f, snap)
    val settled = window(since, SETTLE_AT_MS, 400f)

    paths.outline.reset()
    paths.outline.addRoundRect(RoundRect(r, corner))
    paths.measure.setPath(paths.outline, true)
    val len = paths.measure.length

    // Outline: traced from both ends of the path at once, a spark on each tip; afterwards a thin steady line.
    val trace = if (quiet) 1f else easeInOutCubic(window(since, TRACE_AT_MS, TRACE_MS))
    if (trace > 0f) {
        val outlineColor = Amber.copy(alpha = (if (holding) 0.8f else 0.6f) * a)
        val stroke = Stroke(if (holding) 2.dp.toPx() else 1.5.dp.toPx(), pathEffect = if (holding) holdDash() else null)
        if (trace >= 1f) {
            drawPath(paths.outline, outlineColor, style = stroke)
        } else {
            val half = len * trace / 2f
            paths.segment.reset()
            paths.measure.getSegment(0f, half, paths.segment, true)
            paths.measure.getSegment(len - half, len, paths.segment, true)
            drawPath(paths.segment, Amber.copy(alpha = 0.9f * a), style = Stroke(2.dp.toPx()))
            for (d in floatArrayOf(half, len - half)) {
                val tip = paths.measure.getPosition(d)
                drawCircle(Amber.copy(alpha = 0.3f * a), 7.dp.toPx(), tip)
                drawCircle(Paper.copy(alpha = a), 2.dp.toPx(), tip)
            }
        }
    }

    // Scan: a bright line sweeping top to bottom with a fading amber wake, clipped to the part.
    val scan = if (quiet) window(since, 0f, 420f) else window(since, SCAN_AT_MS, SCAN_MS)
    if (scan > 0f && scan < 1f) {
        val e = easeInOutCubic(scan)
        val y = lerp(r.top, r.bottom, e)
        val fade = (if (quiet) 0.6f else 1f) * minOf(1f, (1f - scan) / 0.2f) * a
        paths.clip.reset()
        paths.clip.addRoundRect(RoundRect(r, corner))
        clipPath(paths.clip) {
            val wake = r.height * 0.4f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Amber.copy(alpha = 0.26f * fade)), startY = y - wake, endY = y),
                Offset(r.left, y - wake),
                Size(r.width, wake),
            )
            drawLine(Amber.copy(alpha = 0.4f * fade), Offset(r.left, y), Offset(r.right, y), 7.dp.toPx())
            drawLine(Paper.copy(alpha = 0.95f * fade), Offset(r.left, y), Offset(r.right, y), 1.5.dp.toPx())
        }
    }

    // Settled: a soft ring breathing out from the box, and a glint lapping the outline every few seconds.
    if (settled > 0f && !holding) {
        val pulse = ((now + i * 377L) % PULSE_MS) / PULSE_MS.toFloat()
        val breathe = 10.dp.toPx() * pulse
        val ring = r.inflate(breathe)
        drawRoundRect(Amber.copy(alpha = 0.5f * (1f - pulse) * settled * a), ring.topLeft, ring.size, CornerRadius(corner.x + breathe), style = Stroke(2.dp.toPx()))

        val glint = ((now + i * 911L) % GLINT_EVERY_MS) / GLINT_MS.toFloat()
        if (glint < 1f) {
            val head = len * easeInOutCubic(glint)
            val tail = len * 0.14f
            val fade = sin(glint * PI.toFloat()) * settled * a
            paths.segment.reset()
            paths.measure.getSegment(maxOf(0f, head - tail), head, paths.segment, true)
            drawPath(paths.segment, Amber.copy(alpha = 0.35f * fade), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(paths.segment, Paper.copy(alpha = 0.9f * fade), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }

    // Brackets last, on top: bloom, amber body and a white-hot core that cools once the lock has settled.
    val arm = minOf(24.dp.toPx(), r.minDimension * 0.32f)
    val breatheScale = if (settled > 0f && !holding) 1f + 0.018f * sin((now % PULSE_MS) / PULSE_MS.toFloat() * 2f * PI.toFloat()) * settled else 1f
    paths.brackets.reset()
    addBrackets(paths.brackets, r, arm, minOf(corner.x, arm * 0.8f))
    val fadeIn = if (quiet) 1f else minOf(1f, since / 90f)
    val ba = a * fadeIn
    val hot = lerp(0.95f, 0.35f, settled)
    withTransform({
        rotate(twist, r.center)
        scale(scale * breatheScale, scale * breatheScale, r.center)
    }) {
        drawPath(paths.brackets, Amber.copy(alpha = 0.1f * ba), style = Stroke(13.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(paths.brackets, Amber.copy(alpha = 0.22f * ba), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(paths.brackets, Amber.copy(alpha = ba), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(paths.brackets, Paper.copy(alpha = hot * ba), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Four L-shaped corners of [r], each [arm] long, rounded to [radius] like the box. */
private fun addBrackets(path: Path, r: Rect, arm: Float, radius: Float) {
    for ((cx, cy, sx, sy) in listOf(
        Quad(r.left, r.top, 1f, 1f), Quad(r.right, r.top, -1f, 1f),
        Quad(r.right, r.bottom, -1f, -1f), Quad(r.left, r.bottom, 1f, -1f),
    )) {
        path.moveTo(cx, cy + sy * arm)
        path.lineTo(cx, cy + sy * radius)
        path.quadraticTo(cx, cy, cx + sx * radius, cy)
        path.lineTo(cx + sx * arm, cy)
    }
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

/** A point part (a screw): its ring springs down from wide while four ticks twist and converge on it. */
private fun DrawScope.drawPointLock(r: Rect, since: Float, now: Long, i: Int, quiet: Boolean, holding: Boolean, a: Float) {
    val snap = if (quiet) 1f else springOut(since / 1000f, 0.5f, 17f)
    val radius = r.width / 2f * lerp(2.2f, 1f, snap)
    val color = Amber.copy(alpha = a * minOf(1f, if (quiet) 1f else since / 90f))
    val dash = if (holding) holdDash() else null
    val settled = window(since, SETTLE_AT_MS * 0.6f, 300f)
    if (settled > 0f && !holding) {
        val pulse = ((now + i * 211L) % PULSE_MS) / PULSE_MS.toFloat()
        drawCircle(Amber.copy(alpha = 0.55f * (1f - pulse) * settled * a), radius + 10.dp.toPx() * pulse, r.center, style = Stroke(2.dp.toPx()))
    }
    drawCircle(Amber.copy(alpha = 0.2f * color.alpha), radius, r.center, style = Stroke(7.dp.toPx()))
    drawCircle(color, radius, r.center, style = Stroke(3.dp.toPx(), pathEffect = dash))
    drawCircle(color, 3.dp.toPx(), r.center)
    val gap = lerp(34.dp.toPx(), 5.dp.toPx(), snap)
    val tick = 7.dp.toPx()
    val spin = lerp(45f, 0f, snap)
    withTransform({ rotate(spin, r.center) }) {
        for (k in 0 until 4) {
            val ang = k * PI.toFloat() / 2f
            val dir = Offset(cos(ang), sin(ang))
            val start = r.center + dir * (radius + gap)
            drawLine(color, start, start + dir * tick, 2.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** The dashed outline of a marker whose tracking is wavering. */
private fun DrawScope.holdDash() = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 8.dp.toPx()))

private fun DrawScope.cornerRadius(r: Rect) = CornerRadius(minOf(16.dp.toPx(), r.minDimension / 3f))

/** A small numbered dot at the top-right of a point marker. */
private fun DrawScope.drawBadge(textMeasurer: TextMeasurer, text: String, at: Offset, alpha: Float) {
    val layout = textMeasurer.measure(text, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink))
    val r = maxOf(layout.size.width, layout.size.height) / 2f + 3.dp.toPx()
    drawCircle(Amber.copy(alpha = alpha), r, at)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f), alpha = alpha)
}

/** The part's name in a small chip above the box (below it when there's no room above). */
private fun DrawScope.drawLabel(textMeasurer: TextMeasurer, text: String, box: Rect, alpha: Float, scale: Float = 1f) {
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
    withTransform({ scale(scale, scale, Offset(left + w / 2f, top + h / 2f)) }) {
        drawRoundRect(Ink.copy(alpha = 0.85f * alpha), Offset(left, top), Size(w, h), CornerRadius(h / 2f))
        drawCircle(Amber.copy(alpha = alpha), dot / 2f, Offset(left + padH + dot / 2f, top + h / 2f))
        drawText(layout, topLeft = Offset(left + padH + dot + 8.dp.toPx(), top + padV), alpha = alpha)
    }
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
private const val PULSE_MS = 1400

// Lock-on timeline, ms after the comet lands. Brackets spring from 0; the rest is staged so the eye goes
// flash → iris → brackets → outline → scan → name.
private const val FLASH_MS = 240f
private const val SHOCK_MS = 520f
/** (delay, peak alpha) of each shockwave ring. */
private val SHOCKWAVES = listOf(0f to 0.85f, 110f to 0.45f)
private const val IRIS_MS = 560f
private const val TRACE_AT_MS = 120f
private const val TRACE_MS = 480f
private const val SCAN_AT_MS = 380f
private const val SCAN_MS = 620f
private const val CHIP_AT_MS = 520f
private const val CHIP_MS = 340f
private const val BADGE_AT_MS = 220f
private const val SETTLE_AT_MS = 950f
private const val GLINT_MS = 900
private const val GLINT_EVERY_MS = 3200L
/** Radius of the ring drawn on a point target (a screw). */
private val POINT_RING = 16.dp
/** Label chips drawn at most (one per kind of part). */
private const val MAX_CHIPS = 4
