package com.fixlens.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fixlens.camera.FrameGrabber
import com.fixlens.guide.ConversationContext
import com.fixlens.guide.MemoryRules
import com.fixlens.session.RepairSession
import com.fixlens.session.SessionRepository
import com.fixlens.session.TitleSource
import com.fixlens.session.Turn
import com.fixlens.vision.VlmEngine
import com.fixlens.voice.SpeechInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

class FixLensViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Mic loudness 0..1, kept separate from [state] so the glow can update ~30×/s cheaply. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel

    val frameGrabber = FrameGrabber()
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

    init {
        refreshSessions()
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
        _state.update {
            it.copy(screen = Screen.Sessions, session = null, question = "", answer = "", timing = "", questionFinal = false)
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

    /** Debug hook (see MainActivity.onNewIntent): a typed question, as if spoken. */
    fun debugAsk(text: String) {
        viewModelScope.launch {
            delay(DEBUG_CAMERA_SETTLE_MS) // the intent pauses/resumes the activity; let the camera expose again
            if (_state.value.screen == Screen.Session && _state.value.engineReady) onQuestion(text)
        }
    }

    private fun onPartial(text: String) {
        if (_state.value.screen != Screen.Session) return
        // The user started talking: drop the previous answer, and barge in on one still running.
        if (_state.value.phase == Phase.Thinking || _state.value.phase == Phase.Answering) cancelTurn()
        _state.update {
            it.copy(phase = Phase.Listening, question = text, questionFinal = false, answer = "", timing = "")
        }
    }

    private fun onQuestion(text: String) {
        val session = _state.value.session ?: return
        cancelTurn()
        val id = turn.incrementAndGet()
        val keyframe = frameGrabber.saveKeyframe(
            File(repo.dir(session.id).apply { mkdirs() }, "kf_${System.currentTimeMillis()}.jpg"),
        )
        _state.update { it.copy(question = text, questionFinal = true, answer = "", timing = "", phase = Phase.Thinking) }

        viewModelScope.launch {
            val start = System.currentTimeMillis()
            var firstTokenMs = -1L
            val answer = StringBuilder()
            val result = runCatching {
                context.ask(session, keyframe?.absolutePath, text) { chunk ->
                    answer.append(chunk)
                    if (turn.get() != id) return@ask
                    if (firstTokenMs < 0) firstTokenMs = System.currentTimeMillis() - start
                    _state.update { it.copy(phase = Phase.Answering, answer = it.answer + chunk) }
                }
            }.onFailure { Log.e(TAG, "VLM failed", it) }.getOrNull()
            val total = System.currentTimeMillis() - start
            Log.i(TAG, "Turn $id \"$text\": first token $firstTokenMs ms, total $total ms, ${result?.stats}")

            val finalAnswer = answer.toString().trim()
            if (result == null || result.cancelled || finalAnswer.isEmpty()) {
                keyframe?.delete()
                if (turn.get() == id) {
                    _state.update {
                        it.copy(phase = Phase.Listening, answer = "Sorry, I couldn't look at that. Please try again.")
                    }
                }
                return@launch
            }
            // The model finished this turn, so it is in the model's memory: store it even if the screen moved on.
            saveTurn(session.id, text, finalAnswer, keyframe?.name, total)
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
    }
}
