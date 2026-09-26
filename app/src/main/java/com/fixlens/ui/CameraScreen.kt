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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

    Box(Modifier.fillMaxSize().background(Ink)) {
        CameraPreview(viewModel)
        MarkerOverlay(marker, analysisSize, state.debugTestBox, state.frozen)
        Scrims()

        val listening = state.engineReady && state.phase == Phase.Listening && state.micOn
        val level by viewModel.micLevel.collectAsStateWithLifecycle()
        VoiceGlow(level = level, active = listening, modifier = Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(title = state.session?.title.orEmpty(), onBack = onBack)
            AnimatedVisibility(visible = state.hint != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                HintChip(state.hint.orEmpty())
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
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
                    state.guide?.let { StepBanner(it) }
                    ConversationCard(state)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (history.isNotEmpty()) {
                        HistoryButton(count = history.size, open = showHistory, onClick = { showHistory = !showHistory })
                    }
                }
                MicButton(
                    on = state.micOn,
                    enabled = state.engineReady && state.error == null,
                    onClick = viewModel::toggleMic,
                )
                Spacer(Modifier.weight(1f))
            }
        }
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
private fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Ink.copy(alpha = 0.55f))
                .border(1.dp, Hairline, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Online))
            Spacer(Modifier.width(8.dp))
            Text("On-device", color = Paper, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
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
private fun ConversationCard(state: UiState) {
    val working = state.phase == Phase.Thinking || state.phase == Phase.Answering
    CardSurface(beam = working) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinkingOrb(
                    state = when {
                        working -> OrbState.Thinking
                        state.micOn -> OrbState.Listening
                        else -> OrbState.Idle
                    },
                    size = 22.dp,
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
                    if (state.micOn) "Point the camera at the problem and ask Fixy what's wrong."
                    else "Microphone is paused. Tap the mic to talk to Fixy.",
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
                    Text("Fixy", color = Amber, style = LabelStyle.copy(fontSize = 11.sp))
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
        !state.micOn -> "Paused"
        state.question.isNotEmpty() && !state.questionFinal -> "Hearing you"
        else -> "Listening"
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

@Composable
private fun MicButton(on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "mic")
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Amber.copy(alpha = 0.12f + 0.88f * fill * (if (enabled) 1f else 0.4f)))
            .border(1.dp, if (on) Color.Transparent else Paper.copy(alpha = 0.3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (on) "Pause microphone" else "Resume microphone" },
        contentAlignment = Alignment.Center,
    ) {
        MicGlyph(color = if (on) Ink else Paper, muted = !on, modifier = Modifier.size(26.dp))
    }
}

/** Microphone icon drawn directly, so no icon library is needed. */
@Composable
private fun MicGlyph(color: Color, muted: Boolean, modifier: Modifier = Modifier) {
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
        if (muted) {
            drawLine(color, Offset(w * 0.12f, h * 0.10f), Offset(w * 0.88f, h * 0.90f), stroke, StrokeCap.Round)
        }
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
