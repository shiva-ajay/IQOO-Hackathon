package com.fixlens.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.ir.KeyPress
import com.fixlens.ui.Amber
import com.fixlens.ui.Hairline
import com.fixlens.ui.Ink
import com.fixlens.ui.Muted
import com.fixlens.ui.Paper

/**
 * The keys the remote just sent, dropping out from under the badge: newest on top, at most three, each fading
 * after about two seconds. Every slip shows the key as it looks on a remote (its glyph or its number) and who
 * pressed it, so the user can follow exactly what Fixy did. Repeats count up on the same slip ("×3").
 */
@Composable
fun KeyPressStack(presses: List<KeyPress>, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presses.asReversed().forEachIndexed { index, press ->
            key(press.id) {
                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = shown,
                    enter = fadeIn(tween(160)) + slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 3 },
                ) {
                    KeySlip(press, older = index > 0)
                }
            }
        }
    }
}

@Composable
private fun KeySlip(press: KeyPress, older: Boolean) {
    val still = reducedMotion()
    // The key cap sinks and springs back each time this key is sent (again).
    val sink = remember { Animatable(0f) }
    // Fades itself out just before the controller drops it.
    val fade = remember { Animatable(1f) }
    LaunchedEffect(press.count) {
        fade.snapTo(1f)
        if (!still) {
            sink.snapTo(1f)
            sink.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
        kotlinx.coroutines.delay(FADE_AFTER_MS)
        fade.animateTo(0f, tween(300))
    }
    val who = if (press.byFixy) "Fixy pressed" else "You pressed"
    Row(
        Modifier
            .alpha(fade.value * if (older) 0.72f else 1f)
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.copy(alpha = 0.92f))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "$who ${press.label}" + if (press.count > 1) ", ${press.count} times" else ""
            }
            .padding(start = 7.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap(press, Modifier.graphicsLayer {
            translationY = sink.value * 2.dp.toPx()
            val s = 1f - 0.08f * sink.value
            scaleX = s
            scaleY = s
        })
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    press.label,
                    color = Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (press.count > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("×${press.count}", color = Amber, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
            Text(press.note ?: who, color = Muted, maxLines = 1, style = TextStyle(fontSize = 11.sp))
        }
    }
}

/** A remote key cap with the key's face: its glyph, its number, or a short word. */
@Composable
private fun KeyCap(press: KeyPress, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(36.dp)
            .clip(shape)
            .background(Paper.copy(alpha = 0.1f))
            .border(1.dp, if (press.byFixy) Amber.copy(alpha = 0.7f) else Paper.copy(alpha = 0.2f), shape),
        contentAlignment = Alignment.Center,
    ) {
        KeyFace(press.key, press.face, Paper)
    }
}

@Composable
private fun KeyFace(key: String, face: String?, color: Color) {
    val glyph = Modifier.size(18.dp)
    when (key) {
        "power", "ac_power" -> PowerGlyph(Amber, glyph)
        "mute" -> MuteGlyph(color, glyph)
        "vol_up", "speed_up" -> PlusMinusGlyph(color, plus = true, modifier = glyph)
        "vol_down", "speed_down" -> PlusMinusGlyph(color, plus = false, modifier = glyph)
        "up" -> ArrowGlyph(color, Arrow.Up, glyph)
        "down" -> ArrowGlyph(color, Arrow.Down, glyph)
        "left" -> ArrowGlyph(color, Arrow.Left, glyph)
        "right" -> ArrowGlyph(color, Arrow.Right, glyph)
        "input" -> InputGlyph(color, glyph)
        "menu" -> MenuGlyph(color, glyph)
        "back" -> BackGlyph(color, glyph)
        "home" -> HomeGlyph(color, glyph)
        else -> FaceText(face ?: KEY_TEXT[key] ?: key.take(3), color)
    }
}

@Composable
private fun FaceText(text: String, color: Color) {
    val size = when {
        text.length <= 2 -> 16.sp
        text.length <= 4 -> 12.sp
        else -> 9.sp
    }
    Text(text, color = color, maxLines = 1, style = TextStyle(fontSize = size, fontWeight = FontWeight.SemiBold))
}

private val KEY_TEXT = mapOf(
    "ok" to "OK", "ch_up" to "Ch+", "ch_down" to "Ch−", "blank" to "Blank", "freeze" to "Freeze",
    "speed" to "Speed", "swing" to "Swing", "timer" to "Timer", "mode" to "Mode",
    "ac_swing" to "Swing", "ac_fan" to "Fan", "ac_mode" to "Mode",
)

private const val FADE_AFTER_MS = 1850L
