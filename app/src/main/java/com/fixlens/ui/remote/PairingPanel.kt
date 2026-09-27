package com.fixlens.ui.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.ir.AimPhase
import com.fixlens.ir.DeviceKind
import com.fixlens.ir.PairPhase
import com.fixlens.ir.RemotePanel
import com.fixlens.ui.Amber
import com.fixlens.ui.Hairline
import com.fixlens.ui.Ink
import com.fixlens.ui.Muted
import com.fixlens.ui.Paper
import com.fixlens.ui.fx.OrbState
import com.fixlens.ui.fx.ThinkingOrb

/** What the pairing card can ask the app to do. */
class PanelActions(
    val onTake: () -> Unit,
    val onChangeBrand: () -> Unit,
    /** Go through the codes automatically. */
    val onStart: () -> Unit,
    val onPause: () -> Unit,
    /** Send the current code once (one at a time). */
    val onSend: () -> Unit,
    val onAnswer: (Boolean) -> Unit,
    val onTryCommon: () -> Unit,
    val onOneByOne: () -> Unit,
    val onClose: () -> Unit,
    /** "Same TV as before?" */
    val onSame: (Boolean) -> Unit = {},
    /** The phone is aimed: press the test key. */
    val onAimReady: () -> Unit = {},
    /** "Did it react?" in the device-or-remote test. */
    val onAimAnswer: (Boolean) -> Unit = {},
)

private val PanelShape = RoundedCornerShape(28.dp)

/**
 * The card in the middle of the screen while Fixy takes a remote: reading the device, confirming the brand,
 * pairing code by code, no match, and the short "paired" moment. The camera stays visible around it (aiming
 * and screen checks need it). One primary action per state; steps slide sideways as the pairing moves on.
 */
@Composable
fun PairingPanel(panel: RemotePanel, actions: PanelActions, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(0.9f)
            .clip(PanelShape)
            .background(Ink.copy(alpha = 0.95f))
            .border(1.dp, Hairline, PanelShape)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        AnimatedContent(
            targetState = panel,
            contentKey = { stepKey(it) },
            transitionSpec = {
                (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(150)))
            },
            label = "panel",
        ) { p ->
            when (p) {
                is RemotePanel.Identifying -> Working(
                    title = "Looking at the ${p.kind?.noun ?: "device"}",
                    body = "Hold the camera on it for a moment so I can read the brand.",
                    onCancel = actions.onClose,
                )
                is RemotePanel.Preparing -> Working(
                    title = "Getting the ${p.brand} codes ready",
                    body = "This takes a second.",
                    onCancel = actions.onClose,
                )
                is RemotePanel.Confirm -> Confirm(p, actions)
                is RemotePanel.Pair -> Pairing(p, actions)
                is RemotePanel.NoMatch -> NoMatch(p, actions)
                is RemotePanel.Paired -> Paired(p)
                is RemotePanel.SameDevice -> SameDevice(p, actions)
                is RemotePanel.Aim -> AimTest(p, actions)
            }
        }
    }
}

/** Keeps a step on screen while only its phase changes; a new code or stage slides in. */
private fun stepKey(p: RemotePanel): Any = when (p) {
    is RemotePanel.Pair -> "pair-${p.stage}-${p.attempt}"
    else -> p::class
}

@Composable
private fun Working(title: String, body: String, onCancel: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingOrb(state = OrbState.Thinking, size = 28.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, color = Paper, style = TitleStyle, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(body, color = Muted, style = BodyStyle)
        Spacer(Modifier.height(18.dp))
        SecondaryButton("Cancel", onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Confirm(p: RemotePanel.Confirm, actions: PanelActions) {
    Column {
        Header(title = "${p.brand} ${p.kind.noun}", onClose = actions.onClose)
        Spacer(Modifier.height(4.dp))
        Text("I read the brand from the camera. I'll pair with its remote code by code.", color = Muted, style = BodyStyle)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Take the remote", actions.onTake, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        SecondaryButton("It's a different brand", actions.onChangeBrand, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Pairing(p: RemotePanel.Pair, actions: PanelActions) {
    val noun = p.kind.noun
    val scanning = p.auto && p.phase != PairPhase.Ready
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Header(title = "Pair with ${p.brand} $noun", onClose = actions.onClose)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    p.stage == 2 -> "Code ${p.attempt} worked. One more check."
                    p.phase == PairPhase.Ready && p.auto -> "${p.attempts} codes to try"
                    else -> "Code ${p.attempt} of ${p.attempts}"
                },
                color = Muted,
                style = BodyStyle.copy(fontSize = 13.sp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Progress(p)
        Spacer(Modifier.height(22.dp))
        SendButton(p, onTap = if (p.auto) actions.onStart else actions.onSend)
        Spacer(Modifier.height(12.dp))
        Text(
            statusLine(p),
            color = if (p.detected) Confirmed else Paper,
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(16.dp))
        when {
            scanning -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Pause", actions.onPause, Modifier.weight(1f))
                    PrimaryButton("It responded", { actions.onAnswer(true) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap the moment the $noun reacts. Volume up on this phone works too.",
                    color = Muted,
                    textAlign = TextAlign.Center,
                    style = BodyStyle.copy(fontSize = 12.sp),
                )
            }
            p.phase == PairPhase.Asking -> {
                Text("Did the $noun respond?", color = Paper, style = TitleStyle.copy(fontSize = 18.sp))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("No response", { actions.onAnswer(false) }, Modifier.weight(1f))
                    PrimaryButton("It responded", { actions.onAnswer(true) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Volume keys work too: up for yes, down for no.", color = Muted, style = BodyStyle.copy(fontSize = 12.sp))
            }
            else -> {
                AimHint(p.kind)
                Spacer(Modifier.height(4.dp))
                TextAction(
                    if (p.auto) "Go one code at a time" else "Try the codes automatically",
                    if (p.auto) actions.onSend else actions.onStart,
                )
            }
        }
    }
}

private fun statusLine(p: RemotePanel.Pair): String {
    val noun = p.kind.noun
    if (p.detected) return if (p.kind == DeviceKind.Ac) "Beep heard" else "The $noun reacted"
    return when (p.phase) {
        PairPhase.Ready -> if (p.auto) "Tap to start" else "Send test: ${p.test}"
        PairPhase.Sending -> "Sending ${p.test.lowercase()}"
        PairPhase.Listening -> "Listening for the beep"
        PairPhase.Watching -> if (p.camera) "Watching the screen" else "Say yes the moment the $noun reacts"
        PairPhase.Asking -> "Sent: ${p.test.lowercase()}"
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Amber,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** One dot per distinct code (a sequence, so numbered progress is honest); a bar when there are too many. */
@Composable
private fun Progress(p: RemotePanel.Pair) {
    if (p.attempts <= MAX_DOTS) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..p.attempts) {
                val color = when {
                    i < p.attempt -> Paper.copy(alpha = 0.22f)
                    i == p.attempt -> Amber
                    else -> Paper.copy(alpha = 0.1f)
                }
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Paper.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(p.attempt / p.attempts.toFloat()).height(4.dp).background(Amber))
        }
    }
}

/** The big round "send" key: the remote mark, rippling while a code goes out and pulsing while listening. */
@Composable
private fun SendButton(p: RemotePanel.Pair, onTap: () -> Unit) {
    val still = reducedMotion()
    val busy = p.phase == PairPhase.Sending || p.phase == PairPhase.Listening || p.phase == PairPhase.Watching ||
        (p.auto && p.phase != PairPhase.Ready)
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(p.phase, p.attempt, p.stage) {
        if (p.phase == PairPhase.Sending && !still) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(600, easing = LinearEasing))
        }
    }
    val listen = rememberInfiniteTransition(label = "listen")
    val pulse by listen.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        if ((p.phase == PairPhase.Listening || p.phase == PairPhase.Watching) && !still && !p.detected) {
            // Listening: a ring opening outward, like sound arriving.
            Canvas(Modifier.size(132.dp)) {
                val r = size.minDimension / 2 * (0.72f + 0.28f * pulse)
                drawCircle(Amber.copy(alpha = 0.45f * (1f - pulse)), r, center, style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    when {
                        p.detected -> Confirmed
                        busy -> Amber.copy(alpha = 0.85f)
                        else -> Amber
                    },
                )
                .clickable(enabled = !busy, role = Role.Button, onClick = onTap)
                .semantics {
                    contentDescription = when {
                        p.auto && p.phase == PairPhase.Ready -> "Start trying codes"
                        p.phase == PairPhase.Ready -> "Send test code"
                        else -> "Send this code again"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (p.detected) CheckGlyph(Ink, Modifier.size(40.dp))
            else RemoteGlyph(Ink, Modifier.size(46.dp), wave = if (ripple.value < 1f) ripple.value else -1f, restAlpha = 0.9f)
        }
    }
    if (p.phase == PairPhase.Asking && !p.auto) {
        Text(
            "Send again",
            color = Amber,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onTap)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AimHint(kind: DeviceKind) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AimGlyph(Paper.copy(alpha = 0.8f), Amber, Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            when (kind) {
                DeviceKind.Projector -> "Point the top of your phone at the projector itself, not the screen."
                else -> "Point the top of your phone at the ${kind.noun}. The IR light is on the top edge."
            },
            color = Paper.copy(alpha = 0.85f),
            style = BodyStyle.copy(fontSize = 13.sp),
        )
    }
}

@Composable
private fun NoMatch(p: RemotePanel.NoMatch, actions: PanelActions) {
    Column {
        Header(
            title = when {
                p.wasAuto && p.kind != DeviceKind.Ac -> "No reaction seen"
                p.triedCommon -> "No code worked"
                else -> "No ${p.brand} code worked"
            },
            onClose = actions.onClose,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                p.wasAuto && p.kind != DeviceKind.Ac -> "The camera didn't catch a reaction. It may have missed it, so let's try each code with you watching."
                p.triedCommon -> "This ${p.kind.noun} may not take IR remote signals. Units run from a wall panel usually don't."
                else -> "Some ${p.kind.noun}s use another maker's remote. The most common codes often work."
            },
            color = Muted,
            style = BodyStyle,
        )
        Spacer(Modifier.height(20.dp))
        when {
            p.wasAuto && p.kind != DeviceKind.Ac -> {
                // The camera may simply have missed it: the same codes, one at a time, with the user answering.
                PrimaryButton("Go one code at a time", actions.onOneByOne, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            !p.triedCommon -> {
                PrimaryButton("Try common codes", actions.onTryCommon, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
        }
        SecondaryButton("Pick another brand", actions.onChangeBrand, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Paired(p: RemotePanel.Paired) {
    val view = LocalView.current
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        confirmHaptic(view)
        draw.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Confirmed.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            CheckGlyph(Confirmed, Modifier.size(40.dp), progress = if (reducedMotion()) 1f else draw.value)
        }
        Spacer(Modifier.height(14.dp))
        Text("Paired", color = Paper, style = TitleStyle.copy(fontSize = 20.sp))
        Spacer(Modifier.height(4.dp))
        Text(
            "${p.profile.label}, model ${p.profile.modelNumber}",
            color = Muted,
            style = BodyStyle,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SameDevice(p: RemotePanel.SameDevice, actions: PanelActions) {
    val noun = p.kind.noun
    Column {
        Header(title = "Same $noun as before?", onClose = actions.onClose)
        Spacer(Modifier.height(4.dp))
        Text(
            "Last time I used the ${p.saved.label} remote (model ${p.saved.modelNumber}). If it's the same $noun, I can test it right away.",
            color = Muted,
            style = BodyStyle,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Same $noun", { actions.onSame(true) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        SecondaryButton("A different $noun", { actions.onSame(false) }, Modifier.fillMaxWidth())
    }
}

/**
 * Is it the device or the user's remote? Aim, then Fixy presses one key with its own remote and checks the
 * result with the camera (TV), the beep (AC), or by asking.
 */
@Composable
private fun AimTest(p: RemotePanel.Aim, actions: PanelActions) {
    val noun = p.kind.noun
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Header(title = "Test the $noun", onClose = actions.onClose)
        Text(
            "I'll press ${p.key.lowercase()} with my remote. If the $noun reacts, your own remote is the problem.",
            color = Muted,
            style = BodyStyle.copy(fontSize = 13.sp),
        )
        Spacer(Modifier.height(18.dp))
        when (p.phase) {
            AimPhase.Waiting -> {
                AimHint(p.kind)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("I'm pointing at it", actions.onAimReady, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Or just say \"ready\".", color = Muted, style = BodyStyle.copy(fontSize = 12.sp))
            }
            AimPhase.Sending, AimPhase.Checking -> {
                val detected = p.detected
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(if (detected) Confirmed else Amber.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (detected) CheckGlyph(Ink, Modifier.size(36.dp)) else RemoteGlyph(Ink, Modifier.size(40.dp), restAlpha = 0.9f)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        detected -> "The $noun reacted"
                        p.phase == AimPhase.Sending -> "Pressing ${p.key.lowercase()}"
                        p.kind == DeviceKind.Ac -> "Listening for the beep"
                        else -> "Watching the screen"
                    },
                    color = if (detected) Confirmed else Paper,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            AimPhase.Asking -> {
                Text("Did the $noun react?", color = Paper, style = TitleStyle.copy(fontSize = 18.sp))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("No", { actions.onAimAnswer(false) }, Modifier.weight(1f))
                    PrimaryButton("Yes, it did", { actions.onAimAnswer(true) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Volume keys work too: up for yes, down for no.", color = Muted, style = BodyStyle.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun Header(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Paper, style = TitleStyle, modifier = Modifier.weight(1f))
        IconButton("Close", onClose) { CloseGlyph(Muted, Modifier.size(16.dp)) }
    }
}

private val TitleStyle = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
private val BodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
private const val MAX_DOTS = 14

