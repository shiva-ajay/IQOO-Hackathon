package com.fixlens.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fixlens.camera.FrameGrabber
import com.fixlens.guide.ConversationContext
import com.fixlens.guide.FixyPrompts
import com.fixlens.guide.Command
import com.fixlens.guide.Commands
import com.fixlens.guide.Guide
import com.fixlens.guide.GuideState
import com.fixlens.guide.GuideView
import com.fixlens.guide.entry
import com.fixlens.kb.KbRepository
import com.fixlens.kb.KnowledgeBase
import com.fixlens.kb.Retriever
import com.fixlens.guide.MemoryRules
import com.fixlens.session.RepairSession
import com.fixlens.session.SessionRepository
import com.fixlens.session.TitleSource
import com.fixlens.session.Turn
import com.fixlens.tracking.FlowTracker
import com.fixlens.tracking.MarkerState
import com.fixlens.vision.BoxMapper
import com.fixlens.vision.CoordScale
import com.fixlens.vision.GroundingParser
import com.fixlens.vision.GroundingParser.Grounding
import com.fixlens.vision.ModelBox
import com.fixlens.vision.VlmEngine
import com.fixlens.voice.SpeechInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

enum class Phase { Loading, Listening, Thinking, Answering, Error }

sealed interface Screen {
    data object Sessions : Screen
    data object Session : Screen
}

data class UiState(
    val screen: Screen = Screen.Sessions,
    // Engine (loads once at app start, while the user is on the sessions list)
    val engineReady: Boolean = false,
    val loadingStep: String = "Waking up Fixy",
    val error: String? = null,
    // Sessions list
    val sessions: List<RepairSession> = emptyList(),
    val sessionsLoaded: Boolean = false,
    // Open session
    val session: RepairSession? = null,
    val phase: Phase = Phase.Loading,
    val micOn: Boolean = true,
    /** The user's words: live while speaking, final once the pause is detected. */
    val question: String = "",
    val questionFinal: Boolean = false,
    val answer: String = "",
    val timing: String = "",
    /** Parts pointed at so far in this answer (they stream in one by one before the spoken reply). */
    val partsFound: Int = 0,
    /** The guided repair in progress (M4), for the step card; null when there's none. */
    val guide: GuideView? = null,
    /** "Point the camera at the …" when Fixy was asked to point but gave no usable box. */
    val hint: String? = null,
    // Debug overlays, switched over adb (see MainActivity.onNewIntent).
    val debugTestBox: Boolean = false,
    val debugFreeze: Boolean = false,
    val frozen: FrozenKeyframe? = null,
)

/** Debug: a question's keyframe and the raw VLM box, drawn over the preview to verify the mapping and scale. */
data class FrozenKeyframe(
    val path: String,
    val width: Int,
    val height: Int,
    /** 0 for a file keyframe (not from the camera). */
    val analysisWidth: Int,
    val analysisHeight: Int,
    val raw: List<ModelBox>,
    val note: String,
)

class FixLensViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Mic loudness 0..1, kept separate from [state] so the glow can update ~30×/s cheaply. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel

    private val tracker = FlowTracker(onReGround = ::onReGroundRequest)
    val frameGrabber = FrameGrabber(onFrame = tracker::onFrame)
    /** The marker in analysis space, ~30 updates a second (kept out of [state] like [micLevel]). */
    val marker: StateFlow<MarkerState?> = tracker.state
    val analysisSize = frameGrabber.analysisSize
    private val vlm = VlmEngine()
    private val context = ConversationContext(vlm)
    private val filesDir = app.getExternalFilesDir(null)!!
    private val repo = SessionRepository(File(app.filesDir, "sessions"))
    private val speech = SpeechInput(
        sttDir = File(filesDir, "stt"),
        onPartial = ::onPartial,
        onFinal = ::onQuestion,
        onLevel = { _micLevel.value = it },
    )

    /** Incremented per question; output from an older (cancelled) turn is ignored. */
    private val turn = AtomicInteger(0)

    private var coordScale = COORD_SCALE
    private var groundingStyle = FixyPrompts.GroundingStyle.Parts
    private var reGroundJob: Job? = null
    /** Re-grounds used since the last question (capped, so a hopeless target doesn't keep the VLM busy). */
    private var reGrounds = 0
    /** The knowledge base (M4), loaded at start; null until then or if it's missing or invalid. */
    @Volatile private var kb: KnowledgeBase? = null
    @Volatile private var retriever: Retriever? = null
    private var guideState: GuideState = GuideState.Idle
    /** Points at the current step's part, then runs its auto-check. */
    private var guideJob: Job? = null

    init {
        refreshSessions()
        viewModelScope.launch(Dispatchers.IO) { loadKb() }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loadingStep = "Loading speech recognition") }
            if (!speech.load()) {
                fail("Speech model not found on this phone.")
                return@launch
            }
            _state.update { it.copy(loadingStep = "Loading Fixy's vision model") }
            if (!vlm.load(modelDir = File(filesDir, "models/qwen3-vl-4b"), tmpDir = File(app.cacheDir, "mnn"))) {
                fail("Vision model not found or failed to load.")
                return@launch
            }
            _state.update { it.copy(engineReady = true, phase = Phase.Listening) }
            launch(Dispatchers.Main) { onSessionScreenReady() }
        }
    }

    // ---- Sessions list ----

    fun sessionDir(id: String): File = repo.dir(id)

    fun newSession() = open(repo.create())

    fun openSession(id: String) {
        viewModelScope.launch {
            repo.load(id)?.let(::open)
        }
    }

    private fun open(session: RepairSession) {
        frameGrabber.clear()
        clearMarker()
        endGuide()
        val last = session.turns.lastOrNull()
        _state.update {
            it.copy(
                screen = Screen.Session,
                session = session,
                phase = if (it.engineReady) Phase.Listening else it.phase,
                question = last?.question.orEmpty(),
                questionFinal = true,
                answer = last?.answer.orEmpty(),
                timing = if (last != null) "Earlier" else "",
                hint = null,
                frozen = null,
            )
        }
        onSessionScreenReady()
    }

    /** Starts listening and warms the model's memory once both the screen and the engine are ready. */
    private fun onSessionScreenReady() {
        val s = _state.value
        if (s.screen != Screen.Session || !s.engineReady) return
        val session = s.session ?: return
        if (s.micOn) speech.start()
        viewModelScope.launch { runCatching { context.prewarm(session) }.onFailure { Log.e(TAG, "Prewarm failed", it) } }
    }

    fun closeSession() {
        cancelTurn()
        speech.stop()
        frameGrabber.clear()
        clearMarker()
        endGuide()
        _state.update {
            it.copy(
                screen = Screen.Sessions, session = null, question = "", answer = "", timing = "", questionFinal = false,
                hint = null, frozen = null,
            )
        }
        refreshSessions()
    }

    fun renameSession(id: String, title: String) {
        val clean = title.trim().take(40)
        if (clean.isEmpty()) return
        updateSession(id) { it.copy(title = clean, titleSource = TitleSource.User) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            context.invalidate(id)
            refreshSessions()
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch {
            val list = repo.list()
            _state.update { it.copy(sessions = list, sessionsLoaded = true) }
        }
    }

    // ---- Conversation ----

    fun toggleMic() {
        val on = !_state.value.micOn
        _state.update { it.copy(micOn = on) }
        if (!_state.value.engineReady || _state.value.screen != Screen.Session) return
        if (on) speech.start() else speech.stop()
    }

    /**
     * Debug hook (see MainActivity.onNewIntent): a typed question, as if spoken. `point: <phrase>` forces a
     * grounding target; [imagePath] replaces the camera frame with an image file (nothing is tracked then).
     */
    fun debugAsk(text: String, imagePath: String? = null) {
        viewModelScope.launch {
            delay(DEBUG_CAMERA_SETTLE_MS) // the intent pauses/resumes the activity; let the camera expose again
            if (_state.value.screen != Screen.Session || !_state.value.engineReady) return@launch
            val target = if (text.startsWith("point:", ignoreCase = true)) text.substringAfter(':').trim() else null
            ask(if (target != null) "Where is the $target?" else text, target, imagePath?.let(::File))
        }
    }

    /** Debug hook: overlays and grounding settings (null leaves a setting as it is). */
    fun debugSettings(testBox: Boolean?, freeze: Boolean?, grounding: String?, coords: String?) {
        grounding?.let { g -> groundingStyle = FixyPrompts.GroundingStyle.entries.firstOrNull { it.name.equals(g, true) } ?: groundingStyle }
        coords?.let { coordScale = if (it.startsWith("px", true)) CoordScale.ABSOLUTE_PIXELS else CoordScale.NORMALIZED_1000 }
        _state.update {
            it.copy(
                debugTestBox = testBox ?: it.debugTestBox,
                debugFreeze = freeze ?: it.debugFreeze,
                frozen = if (freeze == false) null else it.frozen,
            )
        }
        Log.i(TAG, "Debug: testBox=${_state.value.debugTestBox} freeze=${_state.value.debugFreeze} grounding=$groundingStyle coords=$coordScale")
    }

    private fun onPartial(text: String) {
        if (_state.value.screen != Screen.Session) return
        // The user started talking: drop the previous answer, and barge in on one still running.
        if (_state.value.phase == Phase.Thinking || _state.value.phase == Phase.Answering) cancelTurn()
        _state.update {
            it.copy(phase = Phase.Listening, question = text, questionFinal = false, answer = "", timing = "")
        }
    }

    private fun onQuestion(text: String) = ask(text)

    /**
     * One question turn: grab the keyframe, stream the reply through [GroundingParser], seed the tracker the
     * moment the box line is parsed (before the text finishes), and stream the rest into the answer card.
     * [target] is the phrase to point at (debug `point:` now, KB steps in M4); [image] stands in for the camera.
     */
    private fun ask(question: String, target: String? = null, image: File? = null) {
        val session = _state.value.session ?: return
        // A KB match starts a guided repair, and "done", "back"… drive it: the words come from the KB, not the VLM.
        if (target == null && image == null && handleGuide(question)) return
        // A follow-up question during a guided step points at that step's part.
        val pointTarget = target ?: Guide.instruction(guideState)?.target
        cancelTurn()
        clearMarker()
        reGrounds = 0
        val id = turn.incrementAndGet()
        val start = System.currentTimeMillis()
        _state.update {
            it.copy(
                question = question, questionFinal = true, answer = "", timing = "", phase = Phase.Thinking,
                hint = null, frozen = null, partsFound = 0,
            )
        }

        viewModelScope.launch {
            val file = File(repo.dir(session.id).apply { mkdirs() }, "kf_${System.currentTimeMillis()}.jpg")
            val keyframe = if (image != null) {
                withContext(Dispatchers.IO) { FrameGrabber.keyframeFromFile(image, file) }
            } else {
                frameGrabber.captureKeyframe(file)
            }
            val keyframeMs = System.currentTimeMillis() - start
            var firstPartMs = -1L
            var allPartsMs = -1L
            var firstTokenMs = -1L
            var tracked = 0
            val parser = GroundingParser(
                onGrounding = { g ->
                    allPartsMs = System.currentTimeMillis() - start
                    onGrounding(g, keyframe, pointTarget, id)
                },
                onText = text@{ chunk ->
                    if (turn.get() != id) return@text
                    if (firstTokenMs < 0) firstTokenMs = System.currentTimeMillis() - start
                    _state.update { it.copy(phase = Phase.Answering, answer = it.answer + chunk) }
                },
                // Each part goes to the tracker as soon as it's parsed, so markers appear one by one.
                onTarget = { raw ->
                    if (firstPartMs < 0) firstPartMs = System.currentTimeMillis() - start
                    if (trackPart(raw, keyframe, pointTarget, id, first = tracked == 0)) tracked++
                    if (turn.get() == id) _state.update { it.copy(partsFound = it.partsFound + 1) }
                },
            )
            val result = runCatching {
                context.ask(session, keyframe?.file?.absolutePath, question, FixyPrompts.grounding(pointTarget, groundingStyle)) {
                    parser.feed(it)
                }
            }.onFailure { Log.e(TAG, "VLM failed", it) }.getOrNull()
            parser.finish()
            val total = System.currentTimeMillis() - start
            Log.i(
                TAG,
                "Turn $id \"$question\": keyframe $keyframeMs ms, first part $firstPartMs ms, all parts $allPartsMs ms " +
                    "(${summary(parser.grounding)}), first word $firstTokenMs ms, total $total ms, ${result?.stats}",
            )

            // The saved answer (history, titles, replays) never contains the box line.
            val finalAnswer = parser.text.trim()
            if (result == null || result.cancelled || finalAnswer.isEmpty()) {
                keyframe?.file?.delete()
                if (turn.get() == id) {
                    _state.update {
                        it.copy(phase = Phase.Listening, answer = "Sorry, I couldn't look at that. Please try again.")
                    }
                }
                return@launch
            }
            // The model finished this turn, so it is in the model's memory: store it even if the screen moved on.
            saveTurn(session.id, question, finalAnswer, keyframe?.file?.name, total)
            if (turn.get() != id) return@launch
            _state.update {
                it.copy(
                    phase = Phase.Listening,
                    answer = finalAnswer,
                    timing = "Answered in %.1f s · on-device".format(total / 1000.0),
                )
            }
        }
    }

    private fun saveTurn(sessionId: String, question: String, answer: String, keyframe: String?, ms: Long) {
        updateSession(sessionId) { s ->
            val memory = MemoryRules.update(s.memory, question, answer)
            val keywordTitle = MemoryRules.keywordTitle(memory)
            val retitle = s.titleSource <= TitleSource.Keyword && keywordTitle != null && keywordTitle != s.title
            s.copy(
                turns = s.turns + Turn(s.turns.size + 1, question, answer, keyframe, System.currentTimeMillis(), ms),
                memory = memory,
                title = if (retitle) keywordTitle!! else s.title,
                titleSource = if (retitle) TitleSource.Keyword else s.titleSource,
            )
        }?.let { saved ->
            if (saved.titleSource <= TitleSource.Keyword && saved.turns.size in TITLE_AT_TURNS) requestTitle(saved)
        }
    }

    /** Model box → tracker target in analysis space; null if the box is unusable. */
    private fun toTarget(raw: ModelBox, keyframe: FrameGrabber.Keyframe, fallbackLabel: String?): FlowTracker.Target? {
        val box = BoxMapper.modelToKeyframe(raw, coordScale, keyframe.width, keyframe.height) ?: return null
        return FlowTracker.Target(
            BoxMapper.keyframeToAnalysis(box, keyframe.width, keyframe.height, keyframe.analysisWidth, keyframe.analysisHeight),
            (raw.label ?: fallbackLabel ?: "here").take(MAX_LABEL),
            raw.isPoint,
        )
    }

    /**
     * VLM thread, the moment one part is parsed: the first part of a turn starts a new marker group, the rest
     * join it. Returns whether it went to the tracker.
     */
    private fun trackPart(raw: ModelBox, keyframe: FrameGrabber.Keyframe?, target: String?, id: Int, first: Boolean): Boolean {
        if (turn.get() != id || keyframe == null || keyframe.timestampNs == 0L) return false // file images aren't tracked
        val part = toTarget(raw, keyframe, target) ?: return false
        val seed = FlowTracker.Seed(listOf(part), keyframe.timestampNs) { turn.get() == id }
        if (first) tracker.seed(seed) else tracker.add(seed)
        return true
    }

    /** VLM thread, once the pointing JSON is complete: log it, feed the debug overlay, hint if nothing was found. */
    private fun onGrounding(g: Grounding, keyframe: FrameGrabber.Keyframe?, target: String?, id: Int) {
        if (turn.get() != id) return
        val raws = (g as? Grounding.Targets)?.boxes.orEmpty()
        Log.i(TAG, "Grounding: ${summary(g)} ${raws.joinToString { "${it.label}@[${it.x1.toInt()},${it.y1.toInt()},${it.x2.toInt()},${it.y2.toInt()}]" }} (${keyframe?.width}x${keyframe?.height}, $coordScale)")
        if (keyframe != null && _state.value.debugFreeze) {
            _state.update {
                it.copy(
                    frozen = FrozenKeyframe(
                        keyframe.file.absolutePath, keyframe.width, keyframe.height,
                        keyframe.analysisWidth, keyframe.analysisHeight, raws, summary(g),
                    ),
                )
            }
        }
        if (raws.isEmpty() && target != null) _state.update { it.copy(hint = "Point the camera at the $target") }
    }

    private fun summary(g: Grounding?) = when (g) {
        is Grounding.Targets -> "${g.boxes.size} part(s)"
        null -> "no JSON"
        else -> g.toString()
    }

    /**
     * The tracker lost the parts for over a second (analysis thread). Silently asks the VLM where they are
     * now, as a side request that is rolled back from the KV cache. Only while Fixy is idle and the user
     * isn't talking, one at a time, and at most [MAX_REGROUNDS] per question.
     */
    private fun onReGroundRequest(labels: List<String>) {
        viewModelScope.launch {
            val s = _state.value
            val session = s.session ?: return@launch
            if (s.screen != Screen.Session || s.phase != Phase.Listening || !s.questionFinal) return@launch
            if (reGroundJob?.isActive == true || reGrounds >= MAX_REGROUNDS) return@launch
            reGrounds++
            val id = turn.get()
            val attempt = reGrounds
            reGroundJob = launch {
                val start = System.currentTimeMillis()
                val keyframe = frameGrabber.captureKeyframe(File(getApplication<Application>().cacheDir, "reground.jpg"))
                    ?: return@launch
                // Stop generating once the JSON is in; the rest is never used.
                val parser = GroundingParser(onGrounding = { vlm.cancel() }, onText = {})
                val result = runCatching {
                    context.locate(session, keyframe.file.absolutePath, labels) { parser.feed(it) }
                }.onFailure { Log.e(TAG, "Re-ground failed", it) }.getOrNull()
                parser.finish()
                val parts = (parser.grounding as? Grounding.Targets)?.boxes.orEmpty()
                    .mapNotNull { toTarget(it, keyframe, labels.firstOrNull()) }
                Log.i(
                    TAG,
                    "Re-ground $attempt/$MAX_REGROUNDS $labels: ${summary(parser.grounding)}, ${parts.size} usable in " +
                        "${System.currentTimeMillis() - start} ms (${result?.stats ?: "skipped, VLM busy"})",
                )
                if (parts.isEmpty() || turn.get() != id) return@launch
                tracker.seed(FlowTracker.Seed(parts, keyframe.timestampNs) { turn.get() == id })
            }
        }
    }

    // ---- Guided repair (M4): a KB entry's safety lines, then its steps, each pointing at the step's part ----

    private fun loadKb() {
        val app = getApplication<Application>()
        val loaded = runCatching { KbRepository.load(app) }
            .onFailure { Log.e(TAG, "KB failed to load", it) }.getOrNull() ?: return
        val problems = KbRepository.problems(loaded)
        if (problems.isNotEmpty()) {
            val message = "KB ${loaded.version} is invalid:\n" + problems.joinToString("\n")
            // CLAUDE.md §7: fail loudly in debug builds.
            check(app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) { message }
            Log.e(TAG, message)
            return
        }
        kb = loaded
        retriever = Retriever(loaded)
        Log.i(TAG, "KB ${loaded.version}: ${loaded.entries.size} entries")
    }

    /**
     * Main thread. A command ("done", "back"…) or a danger sign for the guide in progress, or a question that
     * matches a KB entry (which starts its guide). Returns false for anything else: a normal question, which
     * during a guide the VLM answers as a follow-up, pointing at the current step's part.
     */
    private fun handleGuide(text: String): Boolean {
        val entry = guideState.entry()
        if (entry != null && guideState !is GuideState.Done) {
            Commands.parse(text)?.let { command ->
                val (next, reminder) = Guide.onCommand(guideState, command)
                when {
                    next == GuideState.Idle -> {
                        endGuide()
                        clearMarker()
                        _state.update { it.copy(question = text, questionFinal = true, answer = GUIDE_STOPPED, timing = "") }
                    }
                    reminder != null -> _state.update { it.copy(question = text, questionFinal = true, hint = reminder) }
                    else -> showGuide(text, next)
                }
                return true
            }
            Guide.escalation(entry, text)?.let { sign ->
                showGuide(text, GuideState.Escalate(entry, sign))
                return true
            }
        }
        val match = retriever?.find(text) as? Retriever.Match.Found ?: return false
        // Asking about the repair already in progress is a follow-up, not a restart.
        if (match.entry.id == entry?.id && guideState !is GuideState.Done) return false
        Log.i(TAG, "KB match ${match.entry.id} (stage ${match.stage}: ${match.why}), kb ${kb?.version}")
        showGuide(text, Guide.start(match.entry))
        return true
    }

    /** Shows a guide state: the KB's words verbatim, then points at the step's part and starts its auto-check. */
    private fun showGuide(question: String, state: GuideState) {
        cancelTurn()
        clearMarker()
        guideJob?.cancel()
        guideState = state
        val ins = Guide.instruction(state) ?: return endGuide()
        _state.update {
            it.copy(
                question = question, questionFinal = true, answer = ins.say, timing = "Repair guide · ${kb?.version}",
                phase = Phase.Listening, hint = null, frozen = null, partsFound = 0, guide = Guide.view(state),
            )
        }
        Log.i(TAG, "Guide ${state.entry()?.id} (kb ${kb?.version}): ${ins.progress} \"${ins.say}\" target=${ins.target} verify=${ins.verify}")
        _state.value.session?.let { saveTurn(it.id, question, ins.say, null, 0) }
        if (ins.target == null && ins.verify == null) return
        guideJob = viewModelScope.launch {
            ins.target?.let { pointAtStep(it) }
            ins.verify?.let { verifyStep(state, it) }
        }
    }

    private fun endGuide() {
        guideJob?.cancel()
        guideJob = null
        guideState = GuideState.Idle
        _state.update { it.copy(guide = null) }
    }

    /** Points at a guide step's target phrase: a rolled-back side request, streamed into the tracker. */
    private suspend fun pointAtStep(target: String) {
        val session = _state.value.session ?: return
        val id = turn.get()
        val start = System.currentTimeMillis()
        val keyframe = frameGrabber.captureKeyframe(File(getApplication<Application>().cacheDir, "step.jpg")) ?: return
        var tracked = 0
        val parser = GroundingParser(
            onGrounding = { vlm.cancel() }, // only the JSON is needed
            onText = {},
            onTarget = { raw ->
                if (trackPart(raw, keyframe, target, id, first = tracked == 0)) tracked++
                if (turn.get() == id) _state.update { it.copy(partsFound = it.partsFound + 1) }
            },
        )
        val result = runCatching {
            context.side(session, keyframe.file.absolutePath, FixyPrompts.pointAt(target), POINT_MAX_TOKENS, wait = true) {
                parser.feed(it)
            }
        }.onFailure { Log.e(TAG, "Step pointing failed", it) }.getOrNull()
        parser.finish()
        Log.i(TAG, "Step pointing \"$target\": ${summary(parser.grounding)} in ${System.currentTimeMillis() - start} ms (${result?.stats})")
        if (tracked == 0 && turn.get() == id) _state.update { it.copy(hint = "Point the camera at the $target") }
    }

    /**
     * The step's visible sign of completion ("the filler cap is off"): every [VERIFY_EVERY_MS] while the user is
     * idle, ask the VLM (rolled back, skipped when it's busy); on "yes", go on as if they'd said "done".
     */
    private suspend fun verifyStep(state: GuideState, sign: String) {
        repeat(MAX_VERIFY_CHECKS) {
            delay(VERIFY_EVERY_MS)
            if (guideState != state) return
            val s = _state.value
            val session = s.session ?: return
            if (s.phase != Phase.Listening || !s.questionFinal) return@repeat // the user is talking: try later
            val keyframe = frameGrabber.captureKeyframe(File(getApplication<Application>().cacheDir, "verify.jpg"))
                ?: return@repeat
            val out = StringBuilder()
            val result = runCatching {
                context.side(session, keyframe.file.absolutePath, FixyPrompts.verify(sign), VERIFY_MAX_TOKENS, wait = false) {
                    out.append(it)
                }
            }.getOrNull()
            val answer = out.toString().trim()
            Log.i(TAG, "Verify \"$sign\": \"$answer\" (${result?.stats ?: "skipped, VLM busy"})")
            if (answer.lowercase().startsWith("yes") && guideState == state) {
                showGuide(SEEN_DONE, Guide.onCommand(state, Command.Done).first)
                return
            }
        }
    }

    private fun clearMarker() {
        tracker.clear()
        reGroundJob?.cancel()
        reGroundJob = null
    }

    /** Asks the model to name the session, in the background; a newer question simply cancels it. */
    private fun requestTitle(session: RepairSession) {
        viewModelScope.launch {
            val title = runCatching { context.suggestTitle(session) }.getOrNull() ?: return@launch
            updateSession(session.id) {
                if (it.titleSource == TitleSource.User) it else it.copy(title = title, titleSource = TitleSource.Model)
            }
        }
    }

    /**
     * Applies [transform] to a session (the open one, or the stored copy) and saves it. Runs on the main
     * thread, so updates never interleave. Returns the updated session.
     */
    private fun updateSession(id: String, transform: (RepairSession) -> RepairSession): RepairSession? {
        val open = _state.value.session?.takeIf { it.id == id }
        val updated = if (open != null) {
            transform(open).copy(updated = System.currentTimeMillis()).also { s -> _state.update { it.copy(session = s) } }
        } else {
            null
        }
        viewModelScope.launch {
            val saved = updated ?: repo.load(id)?.let { transform(it).copy(updated = System.currentTimeMillis()) } ?: return@launch
            repo.save(saved)
            if (_state.value.screen == Screen.Sessions) refreshSessions()
        }
        return updated
    }

    /** Invalidates the current turn and stops its generation at the next token. */
    private fun cancelTurn() {
        turn.incrementAndGet()
        vlm.cancel()
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        _state.update { it.copy(phase = Phase.Error, error = message) }
    }

    override fun onCleared() {
        speech.stop()
        super.onCleared()
    }

    private companion object {
        /** Name the session after the first answer; try again at turn 3 if the model's first try was unusable. */
        val TITLE_AT_TURNS = setOf(1, 3)
        const val DEBUG_CAMERA_SETTLE_MS = 1500L
        /** How the VLM's box numbers are read. Verified on the phone, see docs/marker-tracking.md §2. */
        val COORD_SCALE = CoordScale.NORMALIZED_1000
        const val MAX_REGROUNDS = 5
        const val MAX_LABEL = 40
        const val POINT_MAX_TOKENS = 200
        /** "yes" or "no". */
        const val VERIFY_MAX_TOKENS = 3
        /** A step's auto-check runs this often while the user is idle, at most [MAX_VERIFY_CHECKS] times. */
        const val VERIFY_EVERY_MS = 6000L
        const val MAX_VERIFY_CHECKS = 10
        const val GUIDE_STOPPED = "Okay, we'll stop the guide here."
        /** Shown as the "question" when the auto-check moves on by itself. */
        const val SEEN_DONE = "✓ Looks done"
    }
}
