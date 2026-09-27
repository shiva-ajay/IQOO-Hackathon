package com.fixlens.ui.remote

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.exp

// Glyphs for the remote UI, drawn like the app's mic and keyboard glyphs: round caps, one stroke weight
// (~9% of the glyph), no icon library.

/**
 * A handheld remote with three signal arcs off its top edge: the one recurring mark of "Fixy has the remote".
 * [wave] 0..1 runs one ripple outward (each arc lights in turn); outside a ripple the arcs rest at [restAlpha].
 */
@Composable
fun RemoteGlyph(color: Color, modifier: Modifier = Modifier, arcColor: Color = color, wave: Float = -1f, restAlpha: Float = 0.55f) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.085f
        // Body, tilted a little so the arcs read as "sending up and away".
        val left = w * 0.14f
        val top = w * 0.42f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(w * 0.30f, w * 0.52f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = Stroke(stroke),
        )
        drawCircle(color, w * 0.045f, Offset(left + w * 0.15f, top + w * 0.15f))
        drawLine(color, Offset(left + w * 0.09f, top + w * 0.32f), Offset(left + w * 0.21f, top + w * 0.32f), stroke * 0.8f, StrokeCap.Round)
        val emitter = Offset(left + w * 0.15f, top - w * 0.02f)
        for (i in 0..2) {
            val rest = restAlpha * (1f - 0.18f * i)
            val alpha = if (wave in 0f..1f) maxOf(arcPulse(wave, i), rest * 0.4f) else rest
            val r = w * (0.17f + 0.14f * i)
            drawArc(
                color = arcColor.copy(alpha = arcColor.alpha * alpha.coerceIn(0f, 1f)),
                startAngle = -78f, sweepAngle = 62f, useCenter = false,
                topLeft = Offset(emitter.x - r, emitter.y - r), size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Brightness of arc [i] during a ripple at progress [t]: a bump that passes each arc in turn. */
internal fun arcPulse(t: Float, i: Int): Float {
    val center = 0.18f + 0.28f * i
    val d = (t - center) / 0.16f
    return exp(-d * d)
}

@Composable
fun PowerGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.1f
        val r = w * 0.34f
        drawArc(color, -60f, 300f, false, Offset(w / 2 - r, w / 2 - r + w * 0.04f), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(w / 2, w * 0.08f), Offset(w / 2, w * 0.46f), stroke, StrokeCap.Round)
    }
}

/** A check mark drawn to [progress] (0..1), for the "paired" moment. */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier, progress: Float = 1f) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.1f
        val a = Offset(w * 0.2f, w * 0.52f)
        val b = Offset(w * 0.42f, w * 0.72f)
        val c = Offset(w * 0.8f, w * 0.3f)
        val first = (progress / 0.4f).coerceIn(0f, 1f)
        val second = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
        drawLine(color, a, lerp(a, b, first), stroke, StrokeCap.Round)
        if (second > 0f) drawLine(color, b, lerp(b, c, second), stroke, StrokeCap.Round)
    }
}

@Composable
fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        drawLine(color, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.22f, w * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
fun PlusMinusGlyph(color: Color, plus: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        drawLine(color, Offset(w * 0.2f, w / 2), Offset(w * 0.8f, w / 2), stroke, StrokeCap.Round)
        if (plus) drawLine(color, Offset(w / 2, w * 0.2f), Offset(w / 2, w * 0.8f), stroke, StrokeCap.Round)
    }
}

enum class Arrow { Up, Down, Left, Right }

@Composable
fun ArrowGlyph(color: Color, direction: Arrow, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.11f
        val (tip, a, b) = when (direction) {
            Arrow.Up -> Triple(Offset(w / 2, w * 0.3f), Offset(w * 0.25f, w * 0.62f), Offset(w * 0.75f, w * 0.62f))
            Arrow.Down -> Triple(Offset(w / 2, w * 0.7f), Offset(w * 0.25f, w * 0.38f), Offset(w * 0.75f, w * 0.38f))
            Arrow.Left -> Triple(Offset(w * 0.3f, w / 2), Offset(w * 0.62f, w * 0.25f), Offset(w * 0.62f, w * 0.75f))
            Arrow.Right -> Triple(Offset(w * 0.7f, w / 2), Offset(w * 0.38f, w * 0.25f), Offset(w * 0.38f, w * 0.75f))
        }
        drawLine(color, a, tip, stroke, StrokeCap.Round)
        drawLine(color, b, tip, stroke, StrokeCap.Round)
    }
}

/** A phone seen from the side with its top edge lit and arcs off it: "point the top of the phone". */
@Composable
fun AimGlyph(color: Color, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.075f
        drawRoundRect(color, Offset(w * 0.34f, w * 0.34f), Size(w * 0.32f, w * 0.6f), CornerRadius(w * 0.07f), style = Stroke(stroke))
        drawLine(accent, Offset(w * 0.42f, w * 0.34f), Offset(w * 0.58f, w * 0.34f), stroke * 1.4f, StrokeCap.Round)
        for (i in 0..1) {
            val r = w * (0.14f + 0.12f * i)
            drawArc(accent.copy(alpha = 0.9f - 0.3f * i), -135f, 90f, false, Offset(w / 2 - r, w * 0.3f - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun SearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.1f
        drawCircle(color, w * 0.28f, Offset(w * 0.42f, w * 0.42f), style = Stroke(stroke))
        drawLine(color, Offset(w * 0.63f, w * 0.63f), Offset(w * 0.86f, w * 0.86f), stroke, StrokeCap.Round)
    }
}

/** Speaker with a cross, for mute. */
@Composable
fun MuteGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.09f
        speaker(color, stroke)
        drawLine(color, Offset(w * 0.64f, w * 0.38f), Offset(w * 0.86f, w * 0.62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, w * 0.38f), Offset(w * 0.64f, w * 0.62f), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.speaker(color: Color, stroke: Float) {
    val w = size.width
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.12f, w * 0.38f)
        lineTo(w * 0.28f, w * 0.38f)
        lineTo(w * 0.48f, w * 0.2f)
        lineTo(w * 0.48f, w * 0.8f)
        lineTo(w * 0.28f, w * 0.62f)
        lineTo(w * 0.12f, w * 0.62f)
        close()
    }
    drawPath(path, color, style = Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Input/source: a screen with an arrow coming in. */
@Composable
fun InputGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.09f
        drawRoundRect(color, Offset(w * 0.3f, w * 0.2f), Size(w * 0.58f, w * 0.48f), CornerRadius(w * 0.06f), style = Stroke(stroke))
        drawLine(color, Offset(w * 0.08f, w * 0.44f), Offset(w * 0.54f, w * 0.44f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.32f), Offset(w * 0.54f, w * 0.44f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.56f), Offset(w * 0.54f, w * 0.44f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.48f, w * 0.82f), Offset(w * 0.7f, w * 0.82f), stroke, StrokeCap.Round)
    }
}

@Composable
fun MenuGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.1f
        for (i in 0..2) {
            val y = w * (0.28f + 0.22f * i)
            drawLine(color, Offset(w * 0.2f, y), Offset(w * 0.8f, y), stroke, StrokeCap.Round)
        }
    }
}

/** Back: an arrow turning back on itself. */
@Composable
fun BackGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.1f
        drawArc(color, -90f, 180f, false, Offset(w * 0.3f, w * 0.26f), Size(w * 0.48f, w * 0.48f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.54f, w * 0.26f), Offset(w * 0.24f, w * 0.26f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, w * 0.26f), Offset(w * 0.36f, w * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, w * 0.26f), Offset(w * 0.36f, w * 0.38f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.54f, w * 0.74f), Offset(w * 0.3f, w * 0.74f), stroke, StrokeCap.Round)
    }
}

@Composable
fun HomeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.09f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.16f, w * 0.48f)
            lineTo(w * 0.5f, w * 0.18f)
            lineTo(w * 0.84f, w * 0.48f)
            moveTo(w * 0.26f, w * 0.4f)
            lineTo(w * 0.26f, w * 0.82f)
            lineTo(w * 0.74f, w * 0.82f)
            lineTo(w * 0.74f, w * 0.4f)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
