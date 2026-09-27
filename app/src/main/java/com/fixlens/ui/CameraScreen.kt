package com.fixlens.ui

import android.util.Log
import android.util.Size
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixlens.app.FixLensViewModel
import com.fixlens.app.Phase
import com.fixlens.app.TAG
import com.fixlens.app.UiState
import com.fixlens.ir.RemotePanel
import com.fixlens.ui.remote.BrandSheet
import com.fixlens.ui.remote.KeyPressStack
import com.fixlens.ui.remote.PairingPanel
import com.fixlens.ui.remote.PanelActions
import com.fixlens.ui.remote.RemoteBadge
import com.fixlens.ui.remote.RemoteMenu
import com.fixlens.ui.remote.RemotePad
import com.fixlens.session.Turn
import com.fixlens.ui.fx.OrbState
import com.fixlens.ui.fx.ThinkingOrb
import com.fixlens.ui.fx.VoiceGlow
import com.fixlens.ui.fx.borderBeam
import java.util.concurrent.Executors

@Composable
fun CameraScreen(viewModel: FixLensViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    // Past turns, minus the one already shown in the conversation card.
    val history = state.session?.turns.orEmpty().let { turns ->
        if (turns.lastOrNull()?.let { it.question == state.question && it.answer == state.answer } == true) turns.dropLast(1)
        else turns
    }

    val marker = viewModel.marker.collectAsStateWithLifecycle()
    val analysisSize by viewModel.analysisSize.collectAsStateWithLifecycle()
    val motion = rememberMarkerMotion(marker)

    // "Fixy takes the remote" (ir/RemoteController): badge, pairing card, brand sheet, pad, badge menu.
    val remote by viewModel.remoteUi.collectAsStateWithLifecycle()
    var showRemoteMenu by remember { mutableStateOf(false) }
    val remoteMenuOpen = showRemoteMenu && remote.profile != null
    // The last card shown, so it can still animate out after the controller clears it.
    var lastPanel by remember { mutableStateOf<RemotePanel?>(null) }
    remote.panel?.let { lastPanel = it }
    val panelActions = remember(viewModel) {
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
        )
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        CameraPreview(viewModel)
        MarkerOverlay(marker, motion, analysisSize, state.debugTestBox, state.frozen)
        Scrims()
        // How to do the guided step: a small card up top, and a cue on the tracked part (turn, pull, pour…).
        StepCueLayer(state.guide, marker, motion)

        val level by viewModel.micLevel.collectAsStateWithLifecycle()
        VoiceGlow(level = level, active = state.talking, modifier = Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(
                title = state.session?.title.orEmpty(),
                onBack = onBack,
                badge = if (remote.badge != null) {
                    { RemoteBadge(remote, onClick = { showRemoteMenu = !showRemoteMenu }) }
                } else {
                    null
                },
            )
            AnimatedVisibility(visible = state.hint != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                HintChip(state.hint.orEmpty())
            }
        }

        // The pairing card sits mid-screen over a light dim, so the device stays visible for aiming.
        AnimatedVisibility(visible = remote.panel != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(260))) {
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.35f)))
        }
        AnimatedVisibility(
            visible = remote.panel != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
            // After "Paired" the card shrinks toward the badge, so the user sees where the remote now lives.
            exit = if (lastPanel is RemotePanel.Paired) {
                fadeOut(tween(320)) + scaleOut(tween(360), targetScale = 0.12f, transformOrigin = TransformOrigin(1f, 0f))
            } else {
                fadeOut(tween(180))
            },
            modifier = Modifier.align(Alignment.Center).padding(bottom = 110.dp),
        ) {
            lastPanel?.let { PairingPanel(it, panelActions) }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = showHistory && history.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
            ) {
                HistoryCard(history)
            }
            when {
                state.error != null -> ErrorCard(state.error.orEmpty())
                !state.engineReady -> LoadingCard(state.loadingStep)
                else -> Column {
                    val picker = remote.picker
                    when {
                        picker != null -> BrandSheet(
                            picker = picker,
                            onKind = viewModel::remotePickerKind,
                            onPick = { viewModel.remotePickBrand(it) },
                            onClose = viewModel::remoteClosePicker,
                        )
                        remote.padOpen -> RemotePad(
                            ui = remote,
                            onCommand = viewModel::remoteCommand,
                            onClose = { viewModel.remotePad(false) },
                        )
                        // The pairing card says what's happening; the conversation card steps aside.
                        remote.panel != null -> Unit
                        else -> {
                            state.guide?.let { StepBanner(it) }
                            ConversationCard(state, orbModifier = Modifier.markerLaunchPad(motion))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val controlsEnabled = state.engineReady && state.error == null
            if (state.typing) {
                TypeBar(onSend = viewModel::askTyped, onClose = { viewModel.setTyping(false) })
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (history.isNotEmpty()) {
                            HistoryButton(count = history.size, open = showHistory, onClick = { showHistory = !showHistory })
                        }
                    }
                    MicButton(
                        held = state.talking,
                        enabled = controlsEnabled,
                        onPress = viewModel::startTalking,
                        onRelease = viewModel::stopTalking,
                    )
                    Box(Modifier.weight(1f).padding(start = 16.dp)) {
                        KeyboardButton(enabled = controlsEnabled, onClick = { viewModel.setTyping(true) })
                    }
                }
            }
        }
        // Over the cards: comets fly from Fixy's orb in the card up to the parts.
        HandoffLayer(marker, motion)

        // Every key the remote sends drops out from under the badge, so the user sees what Fixy pressed.
        if (!remoteMenuOpen) {
            KeyPressStack(
                presses = remote.presses,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 62.dp, end = 12.dp),
            )
        }

        if (remoteMenuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showRemoteMenu = false },
            )
            RemoteMenu(
                ui = remote,
                onPad = { showRemoteMenu = false; viewModel.remotePad(true) },
                onPairAgain = { showRemoteMenu = false; viewModel.remotePairAgain() },
                onRelease = { showRemoteMenu = false; viewModel.remoteRelease() },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 62.dp, end = 12.dp),
            )
        }
    }
    BackHandler(enabled = state.typing) { viewModel.setTyping(false) }
    // Back closes the remote's layers one at a time before leaving the session.
    BackHandler(enabled = !state.typing && (remoteMenuOpen || remote.padOpen || remote.picker != null || remote.panel != null)) {
        when {
            remoteMenuOpen -> showRemoteMenu = false
            remote.padOpen -> viewModel.remotePad(false)
            remote.picker != null -> viewModel.remoteClosePicker()
            else -> viewModel.remoteCancel()
        }
    }
}

/** Typed question input: auto-focuses so the keyboard opens at once; the mic button returns to voice. */
@Composable
private fun TypeBar(onSend: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val canSend = text.isNotBlank()
    fun send() {
        if (!canSend) return
        keyboard?.hide()
        onSend(text)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Ink.copy(alpha = 0.88f))
                .border(1.dp, Amber.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (text.isEmpty()) Text("Ask Fixy…", color = Muted, style = QuestionStyle)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_TYPED) },
                    textStyle = QuestionStyle.copy(color = Paper),
                    cursorBrush = SolidColor(Amber),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Amber else Paper.copy(alpha = 0.12f))
                    .clickable(enabled = canSend, onClick = ::send)
                    .semantics { contentDescription = "Send question" },
                contentAlignment = Alignment.Center,
            ) {
                SendGlyph(color = if (canSend) Ink else Muted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Ink.copy(alpha = 0.6f))
                .border(1.dp, Paper.copy(alpha = 0.3f), CircleShape)
                .clickable {
                    keyboard?.hide()
                    onClose()
                }
                .semantics { contentDescription = "Back to voice" },
            contentAlignment = Alignment.Center,
        ) {
            MicGlyph(color = Paper, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun KeyboardButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.6f))
            .border(1.dp, Paper.copy(alpha = 0.3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Type a question" },
        contentAlignment = Alignment.Center,
    ) {
        KeyboardGlyph(color = Paper.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
    }
}

/** Keyboard icon: a key-cap outline, two rows of keys and a space bar. */
@Composable
private fun KeyboardGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.08f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.04f, h * 0.20f),
            size = GeoSize(w * 0.92f, h * 0.60f),
            cornerRadius = CornerRadius(w * 0.12f),
            style = Stroke(stroke),
        )
        val key = w * 0.09f
        for (row in 0..1) {
            val y = h * (0.36f + 0.14f * row)
            for (col in 0..4) {
                drawCircle(color, key / 2f, Offset(w * (0.22f + 0.14f * col), y))
            }
        }
        drawLine(color, Offset(w * 0.32f, h * 0.66f), Offset(w * 0.68f, h * 0.66f), stroke, StrokeCap.Round)
    }
}

/** Up arrow for the send button. */
@Composable
private fun SendGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = w * 0.14f
        drawLine(color, Offset(w * 0.5f, w * 0.88f), Offset(w * 0.5f, w * 0.14f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, w * 0.44f), Offset(w * 0.5f, w * 0.12f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, w * 0.44f), Offset(w * 0.5f, w * 0.12f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun Scrims() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.75f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(420.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.85f)))),
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, badge: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Ink.copy(alpha = 0.55f))
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back to repairs" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) {
                val w = size.width
                val stroke = w * 0.13f
                drawLine(Paper, Offset(w * 0.68f, w * 0.12f), Offset(w * 0.30f, w * 0.5f), stroke, StrokeCap.Round)
                drawLine(Paper, Offset(w * 0.30f, w * 0.5f), Offset(w * 0.68f, w * 0.88f), stroke, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = title,
            transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 }) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "title",
        ) {
            Text(
                it, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.width(12.dp))
        if (badge != null) {
            badge()
            Spacer(Modifier.width(8.dp))
        }
        // With the remote badge up, "On-device" folds to its green dot so the title keeps its room.
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Ink.copy(alpha = 0.55f))
                .border(1.dp, Hairline, RoundedCornerShape(50))
                .semantics { contentDescription = "Running on-device" }
                .padding(horizontal = if (badge != null) 9.dp else 12.dp, vertical = if (badge != null) 9.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Online))
            if (badge == null) {
                Spacer(Modifier.width(8.dp))
                Text("On-device", color = Paper, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun HintChip(text: String) {
    Text(
        text,
        color = Paper,
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Ink.copy(alpha = 0.75f))
            .border(1.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HistoryCard(turns: List<Turn>) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, turns.size - 1))
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(CardShape)
                .background(Ink.copy(alpha = 0.88f)),
        ) {
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                item { Text("EARLIER IN THIS REPAIR", color = Muted, style = LabelStyle.copy(fontSize = 11.sp)) }
                items(turns, key = { it.n }) { turn ->
                    Column(Modifier.padding(top = 14.dp)) {
                        Text("“${turn.question}”", color = Paper.copy(alpha = 0.6f), style = QuestionStyle.copy(fontSize = 14.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(turn.answer, color = Paper, style = QuestionStyle)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HistoryButton(count: Int, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (open) Paper.copy(alpha = 0.16f) else Ink.copy(alpha = 0.6f))
            .border(1.dp, Hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = if (open) "Hide earlier questions" else "Show earlier questions" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = size.width * 0.12f
            for (i in 0..2) {
                val y = size.height * (0.2f + 0.3f * i)
                drawLine(Paper, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("$count earlier", color = Paper, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

private val CardShape = RoundedCornerShape(24.dp)
private const val MAX_TYPED = 200

@Composable
private fun CardSurface(beam: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Ink.copy(alpha = 0.88f))
            .borderBeam(active = beam, shape = CardShape, color = Amber)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .animateContentSize(tween(220)),
    ) { content() }
}

@Composable
private fun ConversationCard(state: UiState, orbModifier: Modifier = Modifier) {
    val working = state.phase == Phase.Thinking || state.phase == Phase.Answering
    CardSurface(beam = working) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinkingOrb(
                    state = when {
                        working -> OrbState.Thinking
                        state.talking -> OrbState.Listening
                        else -> OrbState.Idle
                    },
                    size = 22.dp,
                    modifier = orbModifier,
                )
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = statusLabel(state),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "status",
                ) { label -> Text(label.uppercase(), color = Amber, style = LabelStyle) }
                Spacer(Modifier.weight(1f))
                if (state.timing.isNotEmpty()) Text(state.timing, color = Muted, style = TextStyle(fontSize = 11.sp))
            }

            if (state.question.isEmpty() && state.answer.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        state.typing -> "Point the camera at the problem and type your question."
                        else -> "Point the camera at the problem, hold the mic and ask Fixy what's wrong."
                    },
                    color = Muted,
                    style = QuestionStyle,
                )
            }

            if (state.question.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("You", color = Muted, style = LabelStyle.copy(fontSize = 11.sp))
                Spacer(Modifier.height(2.dp))
                val questionAlpha by animateFloatAsState(if (state.questionFinal) 0.9f else 0.6f, label = "q")
                Text("“${state.question}”", color = Paper.copy(alpha = questionAlpha), style = QuestionStyle)
            }

            AnimatedVisibility(visible = state.answer.isNotEmpty(), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fixy", color = Amber, style = LabelStyle.copy(fontSize = 11.sp))
                        if (state.speaking) {
                            Spacer(Modifier.width(8.dp))
                            SpeakingBars()
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(state.answer, color = Paper, style = AnswerStyle)
                }
            }
        }
    }
}

private fun statusLabel(state: UiState): String = when (state.phase) {
    Phase.Thinking -> if (state.partsFound > 0) "Found ${state.partsFound}" else "Looking"
    Phase.Answering -> "Answering"
    else -> when {
        state.typing -> "Typing"
        state.talking -> "Listening"
        state.transcribing -> "Got it"
        state.speaking -> "Speaking"
        else -> "Hold to talk"
    }
}

/** Three small bars bouncing out of step while Fixy's voice plays. */
@Composable
private fun SpeakingBars() {
    val wave = rememberInfiniteTransition(label = "speaking")
    val heights = List(3) { i ->
        wave.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(320 + i * 90), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
        val bar = size.width / 5
        heights.forEachIndexed { i, h ->
            val barHeight = size.height * h.value
            drawRoundRect(
                color = Amber,
                topLeft = Offset(i * 2 * bar, (size.height - barHeight) / 2),
                size = GeoSize(bar, barHeight),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}

@Composable
private fun LoadingCard(step: String) {
    CardSurface(beam = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingOrb(state = OrbState.Thinking, size = 36.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Getting Fixy ready", color = Paper, style = AnswerStyle.copy(fontSize = 17.sp))
                Spacer(Modifier.height(2.dp))
                Text("$step…", color = Muted, style = QuestionStyle.copy(fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    CardSurface {
        Column {
            Text("SETUP NEEDED", color = Amber, style = LabelStyle)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Paper, style = QuestionStyle)
            Spacer(Modifier.height(4.dp))
            Text(
                "Models live in Android/data/com.fixlens/files (models/ and stt/).",
                color = Muted,
                style = QuestionStyle.copy(fontSize = 13.sp),
            )
        }
    }
}

/** Push-to-talk: audio is only kept while this is held down. */
@Composable
private fun MicButton(held: Boolean, enabled: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val scale by animateFloatAsState(if (held) 1.18f else 1f, tween(160), label = "micScale")
    val ring by animateFloatAsState(if (held) 1f else 0f, tween(160), label = "micRing")
    Box(
        Modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        press()
                        // finally: a press cancelled mid-way (the button leaving the screen) still ends the take.
                        try { tryAwaitRelease() } finally { release() }
                    },
                )
            }
            .semantics { contentDescription = "Hold to talk to Fixy" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(Amber.copy(alpha = 0.25f * ring)))
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Amber.copy(alpha = if (enabled) 1f else 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            MicGlyph(color = Ink, modifier = Modifier.size(26.dp))
        }
    }
}

/** Microphone icon drawn directly, so no icon library is needed. */
@Composable
private fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        // capsule
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.34f, h * 0.06f),
            size = GeoSize(w * 0.32f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.16f),
        )
        // cradle
        drawArc(
            color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.20f, h * 0.22f), size = GeoSize(w * 0.60f, h * 0.52f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        // stand
        drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.92f), stroke, StrokeCap.Round)
    }
}

/**
 * Preview + YUV analysis bound as one [UseCaseGroup] with the preview's ViewPort, so the analysis crop rect is
 * exactly what's on screen (the VLM sees what the user sees; analysis → view is a plain scale). Both 16:9.
 * See docs/marker-tracking.md §1.
 */
@Composable
private fun CameraPreview(viewModel: FixLensViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor { r -> Thread(r, "fixlens-analysis") } }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        // A repair is minutes of hands-busy work: the screen must not sleep while the camera is up.
        previewView.keepScreenOn = true
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        fun bind() {
            val viewPort = previewView.viewPort
            if (disposed || viewPort == null) return
            val ratio = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(ratio)
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, viewModel.frameGrabber) }
            val group = UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(analysis).build()
            val provider = providerFuture.get()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
            Log.i(TAG, "Camera bound: view ${previewView.width}x${previewView.height}, viewport ${viewPort.aspectRatio}")
        }
        providerFuture.addListener({
            // The ViewPort needs the view's final size, so bind after its first layout.
            if (previewView.isLaidOut && previewView.width > 0) {
                bind()
            } else {
                previewView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                        if (v.width == 0) return
                        v.removeOnLayoutChangeListener(this)
                        bind()
                    }
                })
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            previewView.keepScreenOn = false
            runCatching { providerFuture.get().unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
