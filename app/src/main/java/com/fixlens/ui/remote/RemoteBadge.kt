package com.fixlens.ui.remote

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.ir.BadgeState
import com.fixlens.ir.RemoteUi
import com.fixlens.ui.Amber
import com.fixlens.ui.Hairline
import com.fixlens.ui.Ink
import com.fixlens.ui.Muted
import com.fixlens.ui.Paper

internal val Danger = Color(0xFFFF5A5F)
internal val Confirmed = Color(0xFF3DDC97)

/** True when the system asks for no animations (Developer options or accessibility). */
@Composable
internal fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Top-right mark that Fixy holds a remote. Breathes while pairing, sits still while ready, and ripples once
 * (with a haptic tick) for every IR signal sent, so each command is seen and felt. Tap for the menu.
 */
@Composable
fun RemoteBadge(ui: RemoteUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state = ui.badge ?: return
    val still = reducedMotion()
    val view = LocalView.current

    // One ripple per send; the first composition doesn't count as a send.
    val ripple = remember { Animatable(1f) }
    val firstSends = remember { ui.sends }
    LaunchedEffect(ui.sends) {
        if (ui.sends == firstSends) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (still) return@LaunchedEffect
        ripple.snapTo(0f)
        ripple.animateTo(1f, tween(RIPPLE_MS, easing = LinearEasing))
    }
    val breathe = rememberInfiniteTransition(label = "badgeBreathe")
    val breath by breathe.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val borderAlpha by animateFloatAsState(if (state == BadgeState.Pairing) 0.7f else 0.25f, tween(220), label = "border")

    val glyphColor = when (state) {
        BadgeState.NoResponse -> Muted
        else -> Amber
    }
    val restAlpha = when (state) {
        BadgeState.Pairing -> if (still) 0.6f else breath
        BadgeState.NoResponse -> 0.35f
        else -> 0.75f
    }
    val label = when {
        state == BadgeState.Pairing -> "Pairing"
        state == BadgeState.NoResponse -> "Check aim"
        ui.task != null -> ui.task
        else -> ui.profile?.label ?: "Remote"
    }
    val description = when (state) {
        BadgeState.Pairing -> "Fixy is pairing with a remote"
        BadgeState.NoResponse -> "Fixy has the ${ui.profile?.label} remote, but it isn't responding. Open remote options"
        else -> "Fixy is controlling the ${ui.profile?.label} remote. Open remote options"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Ink.copy(alpha = 0.72f))
            .border(1.dp, (if (state == BadgeState.NoResponse) Danger else Amber).copy(alpha = borderAlpha), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteGlyph(
            color = glyphColor,
            wave = if (ripple.value < 1f) ripple.value else -1f,
            restAlpha = restAlpha,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.widthIn(max = 150.dp),
        )
    }
}

/** The badge's menu: the pad, pairing again, letting go. */
@Composable
fun RemoteMenu(
    ui: RemoteUi,
    onPad: () -> Unit,
    onPairAgain: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = ui.profile ?: return
    Column(
        modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.copy(alpha = 0.96f))
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(profile.label, color = Paper, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(2.dp))
            Text(
                ui.lastSent?.let { "Last sent: $it" } ?: "Model ${profile.modelNumber}" + if (!profile.verified) ", partly checked" else "",
                color = Muted,
                style = TextStyle(fontSize = 12.sp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        MenuItem("Remote pad", onPad)
        MenuItem("Pair again", onPairAgain)
        MenuItem("Stop controlling", onRelease, color = Danger)
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit, color: Color = Paper) {
    Text(
        text,
        color = color,
        style = TextStyle(fontSize = 15.sp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/** Filled amber action: one per card. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Amber else Amber.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, maxLines = 1, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Outlined secondary action. */
@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Paper) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Paper.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, maxLines = 1, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium))
    }
}

/** A round icon button (close, back): 44 dp target around a small glyph. */
@Composable
internal fun IconButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Haptic for the "paired" moment (CONFIRM where the phone has it). */
internal fun confirmHaptic(view: android.view.View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )
}

/** Press feedback for pad keys: a small squeeze while held. */
internal fun Modifier.pressScale(pressed: Boolean): Modifier = graphicsLayer {
    val s = if (pressed) 0.94f else 1f
    scaleX = s
    scaleY = s
}

private const val RIPPLE_MS = 450
