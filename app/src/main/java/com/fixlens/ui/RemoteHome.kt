package com.fixlens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.app.FixLensViewModel
import com.fixlens.app.UiState
import com.fixlens.ir.BrandPicker
import com.fixlens.ir.DeviceKind
import com.fixlens.ir.RemotePanel
import com.fixlens.ir.RemoteProfile
import com.fixlens.ir.RemoteUi
import com.fixlens.ui.fx.OrbState
import com.fixlens.ui.fx.ThinkingOrb
import com.fixlens.ui.remote.BrandSheet
import com.fixlens.ui.remote.KeyPressStack
import com.fixlens.ui.remote.PairingPanel
import com.fixlens.ui.remote.PanelActions
import com.fixlens.ui.remote.RemotePad

/**
 * The universal remote on the home screen: the remotes paired before (from any repair or from here), a way to add
 * one by picking the device and its brand, and the pad of the one in use. No camera here, so pairing goes by the
 * user's "It responded" (an AC's beep is still heard); everything else is the same controller as in a repair.
 */
@Composable
fun RemoteHome(viewModel: FixLensViewModel, state: UiState, remote: RemoteUi, onMenu: () -> Unit) {
    val held = remote.profile
    val layers = remote.panel != null || remote.picker != null
    var menuFor by remember { mutableStateOf<RemoteProfile?>(null) }
    var renaming by remember { mutableStateOf<RemoteProfile?>(null) }
    var forgetting by remember { mutableStateOf<RemoteProfile?>(null) }
    // Kept while the card and the sheet animate out, so they don't blank mid-fade.
    var lastPanel by remember { mutableStateOf<RemotePanel?>(null) }
    remote.panel?.let { lastPanel = it }
    var lastPicker by remember { mutableStateOf<BrandPicker?>(null) }
    remote.picker?.let { lastPicker = it }

    // Back closes the remote's layers one at a time, then the pad, before leaving the page.
    BackHandler(enabled = layers || held != null) {
        when {
            remote.picker != null -> viewModel.remoteClosePicker()
            remote.panel != null -> viewModel.remoteCancel()
            else -> viewModel.homeCloseRemote()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PageHeader(
                    title = if (held != null && !layers) held.displayName else "Universal remote",
                    subtitle = if (held != null && !layers) {
                        "${held.brand} ${held.kind.noun} · remote ${held.modelNumber}. Aim the top of the phone at it."
                    } else {
                        "Fixy sends the codes from this phone's IR blaster. No internet, no extra app."
                    },
                    onMenu = onMenu,
                    menuDot = state.alerts.any { it.delivered },
                )
                // Fixy's pairing lines; not over the pad (its "what should I do?" is for the voice in a repair).
                state.remoteNote?.takeIf { held == null }?.let {
                    Spacer(Modifier.height(16.dp))
                    FixyNote(it)
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                !remote.available -> item { NoBlaster() }
                held != null && !layers -> item {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        RemotePad(ui = remote, onCommand = viewModel::remoteCommand, onClose = viewModel::homeCloseRemote)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinkButton("Rename") { renaming = held }
                            LinkButton("Pair again", viewModel::remotePairAgain)
                            Spacer(Modifier.weight(1f))
                            LinkButton("All remotes", viewModel::homeCloseRemote)
                        }
                    }
                }
                else -> {
                    if (remote.saved.isNotEmpty()) {
                        item { SectionLabel("SAVED REMOTES", Modifier.padding(top = 12.dp)) }
                        items(remote.saved, key = { "${it.kind.id}|${it.brand}" }) { profile ->
                            SavedRemoteRow(
                                profile = profile,
                                onClick = { viewModel.homeUseRemote(profile) },
                                onLongClick = { menuFor = profile },
                            )
                        }
                    }
                    item {
                        SectionLabel(if (remote.saved.isEmpty()) "PAIR YOUR FIRST REMOTE" else "ADD A REMOTE", Modifier.padding(top = 16.dp))
                    }
                    item { AddRemoteGrid(onPick = viewModel::homeAddRemote) }
                    item {
                        Text(
                            "Pick the device, then its brand. Fixy tries that brand's codes one by one and saves the one that works. " +
                                "Long-press a saved remote to rename or forget it.",
                            color = Muted,
                            style = QuestionStyle.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // Every key sent drops out at the top right, as in a repair.
        KeyPressStack(
            presses = remote.presses,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp),
        )

        // The pairing card, over a dimmed page.
        AnimatedVisibility(visible = remote.panel != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(220))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.7f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    lastPanel?.let {
                        PairingPanel(
                            it,
                            PanelActions(
                                onTake = viewModel::remoteConfirmBrand,
                                onChangeBrand = viewModel::remoteChangeBrand,
                                onStart = viewModel::remoteStartScan,
                                onPause = viewModel::remotePauseScan,
                                onSend = viewModel::remoteSendTest,
                                onAnswer = viewModel::remoteAnswer,
                                onTryCommon = viewModel::remoteTryCommon,
                                onOneByOne = viewModel::remoteOneByOne,
                                onClose = viewModel::remoteCancel,
                                onSame = viewModel::remoteSameDevice,
                                onAimReady = viewModel::remoteAimReady,
                                onAimAnswer = viewModel::remoteAimAnswer,
                            ),
                        )
                    }
                    state.remoteNote?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            it,
                            color = Paper.copy(alpha = 0.8f),
                            style = QuestionStyle.copy(fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            modifier = Modifier.padding(horizontal = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }

        // The brand sheet slides up from the bottom.
        AnimatedVisibility(
            visible = remote.picker != null,
            enter = slideInVertically(tween(240)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastPicker?.let { picker ->
                BrandSheet(
                    picker = picker,
                    onKind = viewModel::remotePickerKind,
                    onPick = { viewModel.remotePickBrand(it) },
                    onClose = viewModel::remoteClosePicker,
                    modifier = Modifier.navigationBarsPadding().imePadding().padding(12.dp),
                )
            }
        }
    }

    menuFor?.let { p ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = InkRaised,
            title = { Text(p.displayName, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("${p.brand} ${p.kind.noun} · remote ${p.modelNumber}", color = Muted) },
            confirmButton = { TextButton(onClick = { renaming = p; menuFor = null }) { Text("Rename", color = Amber) } },
            dismissButton = { TextButton(onClick = { forgetting = p; menuFor = null }) { Text("Forget", color = Paper) } },
        )
    }
    renaming?.let { p ->
        var text by remember(p) { mutableStateOf(p.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = InkRaised,
            title = { Text("Name this remote", color = Paper) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(30) },
                    singleLine = true,
                    placeholder = { Text("e.g. Bedroom ${p.kind.noun}", color = Muted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Paper, unfocusedTextColor = Paper,
                        focusedBorderColor = Amber, unfocusedBorderColor = Hairline, cursorColor = Amber,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.homeRenameRemote(p, text); renaming = null }) { Text("Save", color = Amber) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = Muted) } },
        )
    }
    forgetting?.let { p ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            containerColor = InkRaised,
            title = { Text("Forget this remote?", color = Paper) },
            text = { Text("\"${p.displayName}\" will need pairing again next time.", color = Muted) },
            confirmButton = { TextButton(onClick = { viewModel.homeForgetRemote(p); forgetting = null }) { Text("Forget", color = Amber) } },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancel", color = Muted) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRemoteRow(profile: RemoteProfile, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .homeCard()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Amber.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { DeviceGlyph(profile.kind, Amber, Modifier.size(26.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.displayName, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(2.dp))
            val detail = buildString {
                if (profile.name != null) append(profile.label).append(" · ")
                append("remote ").append(profile.modelNumber)
                if (!profile.verified) append(" · partly checked")
                profile.acState?.let { append(" · last sent ").append(it.describe().lowercase()) }
            }
            Text(detail, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp))
        }
        Spacer(Modifier.width(8.dp))
        ChevronGlyph(Muted, Modifier.size(16.dp))
    }
}

@Composable
private fun AddRemoteGrid(onPick: (DeviceKind) -> Unit) {
    Column(verticalArrangement = SmallGap) {
        DeviceKind.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = SmallGap) {
                row.forEach { kind ->
                    Column(
                        Modifier
                            .weight(1f)
                            .homeCard()
                            .clickable(role = Role.Button) { onPick(kind) }
                            .padding(16.dp),
                    ) {
                        DeviceGlyph(kind, Paper, Modifier.size(28.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(kindName(kind), color = Paper, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                        Text("Pair a remote", color = Muted, style = TextStyle(fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FixyNote(text: String) {
    Row(
        Modifier.homeCard().padding(horizontal = 14.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinkingOrb(state = OrbState.Idle, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Paper, style = QuestionStyle.copy(fontSize = 14.sp))
    }
}

@Composable
private fun NoBlaster() {
    Column(Modifier.homeCard().padding(18.dp)) {
        Text("No IR blaster found", color = Paper, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text(
            "The universal remote needs a phone with an IR emitter, like the iQOO 15. Repairs and alerts still work.",
            color = Muted,
            style = QuestionStyle.copy(fontSize = 14.sp),
        )
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Amber,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private fun kindName(kind: DeviceKind) = when (kind) {
    DeviceKind.Ac -> "AC"
    DeviceKind.Tv -> "TV"
    DeviceKind.Projector -> "Projector"
    DeviceKind.Fan -> "Fan"
}

/** A line drawing of each device the remote can drive. */
@Composable
internal fun DeviceGlyph(kind: DeviceKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(w * 0.08f, cap = StrokeCap.Round)
        val line = w * 0.08f
        when (kind) {
            DeviceKind.Tv -> {
                drawRoundRect(color, Offset(w * 0.08f, w * 0.16f), Size(w * 0.84f, w * 0.56f), CornerRadius(w * 0.08f), style = stroke)
                drawLine(color, Offset(w * 0.36f, w * 0.86f), Offset(w * 0.64f, w * 0.86f), line, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, w * 0.72f), Offset(w * 0.5f, w * 0.86f), line, StrokeCap.Round)
            }
            DeviceKind.Ac -> {
                drawRoundRect(color, Offset(w * 0.06f, w * 0.24f), Size(w * 0.88f, w * 0.38f), CornerRadius(w * 0.1f), style = stroke)
                drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.82f, w * 0.5f), line, StrokeCap.Round)
                // Cool air coming out.
                for (x in listOf(0.3f, 0.5f, 0.7f)) {
                    drawLine(color, Offset(w * x, w * 0.72f), Offset(w * (x - 0.04f), w * 0.86f), line * 0.8f, StrokeCap.Round)
                }
            }
            DeviceKind.Projector -> {
                drawRoundRect(color, Offset(w * 0.08f, w * 0.3f), Size(w * 0.84f, w * 0.42f), CornerRadius(w * 0.1f), style = stroke)
                drawCircle(color, w * 0.12f, Offset(w * 0.66f, w * 0.51f), style = stroke)
                drawLine(color, Offset(w * 0.2f, w * 0.44f), Offset(w * 0.38f, w * 0.44f), line, StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, w * 0.72f), Offset(w * 0.24f, w * 0.82f), line, StrokeCap.Round)
                drawLine(color, Offset(w * 0.76f, w * 0.72f), Offset(w * 0.76f, w * 0.82f), line, StrokeCap.Round)
            }
            DeviceKind.Fan -> {
                val c = Offset(w * 0.5f, w * 0.5f)
                drawCircle(color, w * 0.42f, c, style = stroke)
                drawCircle(color, w * 0.07f, c)
                for (i in 0..2) {
                    val a = Math.toRadians(90.0 + i * 120.0)
                    val tip = Offset(c.x + (w * 0.3f * Math.cos(a)).toFloat(), c.y - (w * 0.3f * Math.sin(a)).toFloat())
                    drawLine(color, c, tip, w * 0.13f, StrokeCap.Round)
                }
            }
        }
    }
}
