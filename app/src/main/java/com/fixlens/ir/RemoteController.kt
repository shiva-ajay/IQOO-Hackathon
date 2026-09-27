package com.fixlens.ir

import android.content.Context
import android.util.Log
import com.fixlens.app.TAG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Where the remote badge is. Null in [RemoteUi.badge] hides it. */
enum class BadgeState { Pairing, Ready, Working, NoResponse }

/** The card in the middle of the screen while Fixy takes a remote. */
sealed interface RemotePanel {
    /** Fixy is reading the device and its brand off the camera. */
    data class Identifying(val kind: DeviceKind?) : RemotePanel

    /** Building the brand's test codes before the first try (a moment). */
    data class Preparing(val kind: DeviceKind, val brand: String) : RemotePanel

    /** "That looks like an LG TV": take its remote, or pick another brand. */
    data class Confirm(val kind: DeviceKind, val brand: String) : RemotePanel

    data class Pair(
        val kind: DeviceKind,
        val brand: String,
        /** 1 = the first test (finds the code), 2 = the second (confirms the model). */
        val stage: Int,
        val attempt: Int,
        val attempts: Int,
        /** What the test presses: "Volume up", "On, cool 24°". */
        val test: String,
        val phase: PairPhase,
        /** Fixy is going through the codes by itself (true) or one at a time with the user (false). */
        val auto: Boolean,
        /** The device's response was noticed: the AC's beep or a change on the screen. */
        val detected: Boolean = false,
    ) : RemotePanel

    data class NoMatch(val kind: DeviceKind, val brand: String, val triedCommon: Boolean, val wasAuto: Boolean) : RemotePanel

    /** The short "paired" moment before the card folds into the badge. */
    data class Paired(val profile: RemoteProfile) : RemotePanel
}

enum class PairPhase {
    /** Before the first code: "Start" (or the next code, one at a time). */
    Ready,
    Sending,
    /** TV/projector: the camera watches the screen for a response. Fan: waiting for the user's tap. */
    Watching,
    /** AC: listening for the beep. */
    Listening,
    /** "Did it respond?" */
    Asking,
}

/** Brand picker sheet: device tabs, suggestions first, then the full list. */
data class BrandPicker(
    val kind: DeviceKind,
    val suggestions: List<String>,
    val featured: List<String>,
    val all: List<String>,
)

data class RemoteUi(
    /** The phone can send IR and the code catalog is loaded. */
    val available: Boolean = false,
    val panel: RemotePanel? = null,
    val picker: BrandPicker? = null,
    val profile: RemoteProfile? = null,
    val badge: BadgeState? = null,
    /** Counts every IR send, so the badge can ripple once per signal. */
    val sends: Int = 0,
    /** The keys just sent, newest last, for the pop-ups under the badge. */
    val presses: List<KeyPress> = emptyList(),
    /** "Volume up", "Cool, 24°, fan auto": the last command sent to the paired device. */
    val lastSent: String? = null,
    /** What Fixy is doing with the remote ("Step 2: looking"), shown on the badge. */
    val task: String? = null,
    val padOpen: Boolean = false,
)

/**
 * "Fixy takes the remote" (docs/ir-remote-plan.md): identifies the device and brand, pairs with one of the
 * brand's remotes by going through the codes itself, then sends commands and runs the remote agent
 * (RemoteAgent: the VLM plans, the remote is its tool, the camera, the AC's beep and the user check the result).
 * Called on the main thread; sends and AC encoding run on the IR thread, VLM looks go through [host].
 */
class RemoteController(
    context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
) {
    /** What the controller needs from the rest of the app (FixLensViewModel). */
    interface Host {
        /** Shows [userText] (if any) and Fixy's [answer] in the conversation card, speaks it and saves the turn. */
        fun reply(userText: String?, answer: String)

        /** Speaks [line] without touching the card (short pairing prompts). */
        fun speak(line: String)

        /** One VLM look at the current frame: which device and brand; null if it couldn't look. */
        suspend fun identify(): Identified?

        /** One VLM yes/no look at the current frame; null if the VLM was busy or had no picture. */
        suspend fun check(sign: String): Boolean?

        /** One VLM step of the remote agent on the current frame: its raw reply, or null if it couldn't look. */
        suspend fun plan(request: String, maxTokens: Int): String?

        /** The paired device changed (null: released); the host stores it with the session. */
        fun onProfile(profile: RemoteProfile?)
    }

    data class Identified(val kind: DeviceKind?, val brand: String?)

    private val appContext = context.applicationContext
    private val blaster = IrBlaster(appContext)
    private val codec = AcCodec(blaster) { name -> IrCatalog.acBinary(appContext.assets, name) }
    private val store = RemoteStore(File(appContext.filesDir, "remotes.json"))
    val beep = BeepProbe()
    val screen = ScreenProbe()
    @Volatile private var catalog: IrCatalog? = null

    private val _ui = MutableStateFlow(RemoteUi())
    val ui: StateFlow<RemoteUi> = _ui

    private var job: Job? = null
    /** Folds the "Paired" card away, then runs what was asked before pairing. Separate from [job], so a command
     *  spoken the moment pairing ends can't cancel it and leave the card on screen. */
    private var cardJob: Job? = null
    private var pairing: Pairing? = null
    private var session: PairSession? = null
    /** Going through the codes by itself (the default) rather than one at a time. */
    private var auto = true
    /** The code whose response window is open, and where the search stood before it moved on (late taps). */
    private var sentStep: Pairing.Step.Test? = null
    private var lastMark: Pairing.Mark? = null
    /** What to do once paired ("turn on the AC", "check the TV", "find the laptop input"). */
    private var pendingCommand: RemoteCommand? = null
    private var pendingRoutine: RemoteRoutine? = null
    private var pendingGoal: String? = null
    /** The brand question is open (the picker is up after Fixy asked): a spoken brand answers it. */
    private var awaitingBrand = false
    private var missedBeeps = 0
    /** The agent asked the user something; their next words answer it. */
    private var agentAnswer: CompletableDeferred<String>? = null
    private var agentRunning = false
    private var pressSeq = 0L
    private var lastPressAt = 0L

    /** A pairing run: the brand's models and each one's two test signals. */
    private class PairSession(
        val kind: DeviceKind,
        val brand: String,
        val models: List<IrCatalog.ModelCodes>,
        val tests: List<Tests?>,
        val common: Boolean,
    )

    /** A model's two test signals, how each shows in the key pop-up, and for ACs the state each one leaves. */
    private class Tests(
        val first: IrPattern,
        val firstLabel: String,
        val firstKey: String,
        val second: IrPattern?,
        val secondLabel: String?,
        val secondKey: String?,
        val firstFace: String? = null,
        val secondFace: String? = null,
        val acFirst: AcState? = null,
        val acSecond: AcState? = null,
    )

    private data class Outcome(val sent: Boolean, val beep: Boolean? = null)

    // ---- Lifecycle ----

    /** Loads the code catalog (IO thread). Without it, or without an IR emitter, remote features stay off. */
    fun load() {
        val loaded = runCatching { IrCatalog.load(appContext.assets) }
            .onFailure { Log.e(TAG, "IR catalog failed to load", it) }.getOrNull()
        catalog = loaded
        Log.i(TAG, "IR: ${blaster.describe()}; catalog ${loaded?.version ?: "missing"}")
        _ui.update { it.copy(available = loaded != null && blaster.available) }
    }

    fun describe(): String = blaster.describe() + "; catalog " + (catalog?.version ?: "missing")

    /** Debug: pretend to send (logs only), to try the flow on a phone without IR. */
    fun setFake(on: Boolean) {
        blaster.fake = on
        _ui.update { it.copy(available = catalog != null && blaster.available) }
    }

    /** A session opened: restore the device it was controlling, if any. */
    fun attach(profile: RemoteProfile?) {
        reset()
        _ui.update { it.copy(profile = profile, badge = profile?.let { BadgeState.Ready }, lastSent = null) }
    }

    /** The session closed: drop anything in progress (the profile stays stored with the session). */
    fun detach() {
        reset()
        _ui.update { it.copy(profile = null, badge = null, lastSent = null) }
    }

    private fun reset() {
        job?.cancel()
        job = null
        cardJob?.cancel()
        cardJob = null
        pairing = null
        session = null
        sentStep = null
        lastMark = null
        pendingCommand = null
        pendingRoutine = null
        pendingGoal = null
        awaitingBrand = false
        missedBeeps = 0
        agentAnswer?.cancel()
        agentAnswer = null
        screen.active = false
        _ui.update { it.copy(panel = null, picker = null, task = null, padOpen = false, presses = emptyList()) }
    }

    // ---- Words ----

    /** Main thread: [text] is about the remote (an answer, a brand, a command). Returns false to let it go on. */
    fun handle(text: String): Boolean {
        if (!_ui.value.available && catalog == null) return false
        val t = text.lowercase()
        // The agent asked something: this is the answer (unless it's "stop").
        agentAnswer?.let { pending ->
            if (!STOP_AGENT.containsMatchIn(t)) {
                pending.complete(text.trim())
                host.reply(text, "Thanks.")
                return true
            }
        }
        if (agentRunning && STOP_AGENT.containsMatchIn(t)) {
            stopAgent(text)
            return true
        }
        val ui = _ui.value
        when (val panel = ui.panel) {
            is RemotePanel.Pair -> return handlePairingWords(text, panel)
            is RemotePanel.Confirm -> {
                when {
                    CANCEL.containsMatchIn(t) -> cancel()
                    RemoteCommands.answer(text) == RemoteCommands.Answer.Yes -> confirmBrand()
                    brandIn(text, panel.kind)?.let { pickBrand(it, panel.kind, text); true } == true -> Unit
                    RemoteCommands.answer(text) == RemoteCommands.Answer.No -> changeBrand()
                    else -> return false
                }
                return true
            }
            is RemotePanel.Identifying, is RemotePanel.Preparing -> return false
            else -> Unit
        }
        if (ui.picker != null && awaitingBrand) {
            val picker = ui.picker
            if (CANCEL.containsMatchIn(t)) {
                closePicker()
                host.reply(text, "Okay, I'll leave the remote.")
                return true
            }
            val match = catalog?.matchBrand(picker.kind, text)
            if (match != null && match.exact) {
                pickBrand(match.best!!, picker.kind, text)
                return true
            }
            if (match != null && match.names.isNotEmpty()) {
                _ui.update { it.copy(picker = picker.copy(suggestions = match.names)) }
                host.reply(text, "Did you mean ${match.names.joinToString(" or ")}? Tap it below.")
                return true
            }
        }
        val profile = ui.profile
        if (profile != null && PAIR_AGAIN.containsMatchIn(t)) {
            pairAgain(text)
            return true
        }
        return when (val parsed = RemoteCommands.parse(text, profile?.kind)) {
            null -> false
            is RemoteCommands.Parsed.Take -> {
                take(parsed.kind, text, parsed.then, parsed.routine, parsed.goal)
                true
            }
            RemoteCommands.Parsed.Release -> {
                release(text)
                true
            }
            is RemoteCommands.Parsed.Command -> {
                run(parsed.command, text, byFixy = true)
                true
            }
            is RemoteCommands.Parsed.Routine -> {
                startAgent(routineGoal(parsed.routine), text, parsed.routine)
                true
            }
            is RemoteCommands.Parsed.Agent -> {
                startAgent(parsed.goal, text)
                true
            }
        }
    }

    /** While pairing: yes/no, start, pause, "send again", "cancel". Everything is taken, so no stray word reaches the VLM. */
    private fun handlePairingWords(text: String, panel: RemotePanel.Pair): Boolean {
        val t = text.lowercase()
        val answer = RemoteCommands.answer(text)
        when {
            CANCEL.containsMatchIn(t) -> cancel()
            panel.auto && panel.phase != PairPhase.Ready && PAUSE.containsMatchIn(t) -> pauseScan()
            panel.phase == PairPhase.Ready && (answer == RemoteCommands.Answer.Yes || START.containsMatchIn(t)) ->
                if (panel.auto) startScan() else sendTest()
            SEND_AGAIN.containsMatchIn(t) -> sendTest()
            // While the codes go by (or after one), "yes, that one" counts for the code just sent.
            answer == RemoteCommands.Answer.Yes && panel.phase != PairPhase.Ready -> answer(true)
            answer == RemoteCommands.Answer.No && !panel.auto && panel.phase == PairPhase.Asking -> answer(false)
            panel.auto -> host.speak("Say yes the moment the ${panel.kind.noun} responds.")
            else -> host.speak("Say yes if the ${panel.kind.noun} responded, or no to try the next code.")
        }
        return true
    }

    /** Whether the volume keys answer pairing right now: up = it responded, down = no (one at a time only). */
    fun volumeKeyUsable(up: Boolean): Boolean {
        val panel = _ui.value.panel as? RemotePanel.Pair ?: return false
        if (panel.phase == PairPhase.Ready || panel.phase == PairPhase.Sending) return false
        return up || !panel.auto
    }

    fun volumeKey(up: Boolean): Boolean {
        if (!volumeKeyUsable(up)) return false
        answer(up)
        return true
    }

    // ---- Taking the remote ----

    /** "Take the remote" / "control the TV": work out the device and brand, then pair (or reuse a pairing). */
    fun take(
        kindHint: DeviceKind?,
        userText: String?,
        then: RemoteCommand? = null,
        routine: RemoteRoutine? = null,
        goal: String? = null,
    ) {
        val catalog = catalog
        if (catalog == null || !blaster.available) {
            host.reply(userText, "This phone has no IR blaster, so I can't use a remote here.")
            return
        }
        val profile = _ui.value.profile
        if (profile != null && (kindHint == null || kindHint == profile.kind)) {
            when {
                then != null -> run(then, userText, byFixy = true)
                routine != null -> startAgent(routineGoal(routine), userText, routine)
                goal != null -> startAgent(goal, userText)
                else -> host.reply(userText, "I already have the ${profile.label} remote.")
            }
            return
        }
        reset()
        pendingCommand = then
        pendingRoutine = routine
        pendingGoal = goal
        // A brand said outright ("control the LG TV") needs no look and no confirmation.
        val spoken = kindHint?.let { k -> userText?.let { catalog.matchBrand(k, it) }?.takeIf { it.exact }?.best }
        if (kindHint != null && spoken != null) {
            userText?.let { host.reply(it, "Okay, the $spoken ${kindHint.noun}.") }
            startPairing(kindHint, spoken)
            return
        }
        _ui.update { it.copy(panel = RemotePanel.Identifying(kindHint)) }
        userText?.let { host.reply(it, "Let me see which ${kindHint?.noun ?: "device"} this is.") }
        job = scope.launch {
            val seen = runCatching { host.identify() }.onFailure { Log.e(TAG, "IR identify failed", it) }.getOrNull()
            val kind = kindHint ?: seen?.kind
            Log.i(TAG, "IR identify: saw ${seen?.kind}/${seen?.brand}, hint $kindHint")
            if (kind == null) {
                openPicker(DeviceKind.Tv, emptyList())
                host.reply(null, "I can use the remote for ACs, TVs, projectors and fans. Pick the device and its brand below.")
                return@launch
            }
            val match = seen?.brand?.let { catalog.matchBrand(kind, it) }
            when {
                match != null && match.exact -> {
                    _ui.update { it.copy(panel = RemotePanel.Confirm(kind, match.best!!)) }
                    host.reply(null, "That looks like ${article(match.best!!)} ${match.best} ${kind.noun}. Shall I take its remote?")
                }
                match != null && match.names.isNotEmpty() -> {
                    openPicker(kind, match.names)
                    host.reply(null, "I couldn't read the brand clearly. Is it one of these?")
                }
                else -> {
                    openPicker(kind, emptyList())
                    host.reply(null, "I can't see a brand name on it. What brand is the ${kind.noun}?")
                }
            }
        }
    }

    fun confirmBrand() {
        val panel = _ui.value.panel as? RemotePanel.Confirm ?: return
        startPairing(panel.kind, panel.brand)
    }

    /** "Not this brand": open the picker on the same device. */
    fun changeBrand() {
        val kind = (_ui.value.panel as? RemotePanel.Confirm)?.kind ?: (_ui.value.panel as? RemotePanel.NoMatch)?.kind
            ?: _ui.value.picker?.kind ?: DeviceKind.Tv
        screen.active = false
        openPicker(kind, emptyList())
        host.speak("Which brand is it?")
    }

    /** The picker's device tabs. */
    fun pickerKind(kind: DeviceKind) {
        val picker = _ui.value.picker ?: return
        if (picker.kind != kind) openPicker(kind, emptyList())
    }

    fun pickBrand(brand: String, kind: DeviceKind, userText: String? = null) {
        userText?.let { host.reply(it, "Okay, $brand.") }
        startPairing(kind, brand)
    }

    fun closePicker() {
        awaitingBrand = false
        _ui.update { it.copy(picker = null, panel = if (it.panel is RemotePanel.Identifying) null else it.panel) }
    }

    private fun openPicker(kind: DeviceKind, suggestions: List<String>) {
        val catalog = catalog ?: return
        awaitingBrand = true
        _ui.update {
            it.copy(
                panel = null,
                picker = BrandPicker(
                    kind = kind,
                    suggestions = suggestions,
                    featured = catalog.featured(kind),
                    all = catalog.brands(kind).map { b -> b.brand },
                ),
            )
        }
    }

    // ---- Pairing ----

    private fun startPairing(kind: DeviceKind, brand: String, common: Boolean = false, useSaved: Boolean = true, oneByOne: Boolean = false) {
        val catalog = catalog ?: return
        awaitingBrand = false
        job?.cancel()
        _ui.update { it.copy(picker = null, panel = RemotePanel.Preparing(kind, brand), badge = BadgeState.Pairing) }
        job = scope.launch {
            val saved = if (useSaved && !common) withContext(Dispatchers.IO) { store.find(kind, brand) } else null
            if (saved != null && modelFor(saved) != null) {
                Log.i(TAG, "IR: reusing saved ${saved.label} model ${saved.modelId}")
                becomePaired(saved, announce = "I've used this ${saved.label} remote before, so I'll use it again. " +
                    "If it doesn't respond, say \"pair again\".")
                return@launch
            }
            val models = if (common) catalog.common(kind) else catalog.brand(kind, brand)?.models.orEmpty()
            val tests = models.map { m -> runCatching { testsFor(kind, m) }.getOrNull() }
            val candidates = tests.mapIndexed { i, t -> Pairing.Candidate(i, t?.first?.key, t?.second?.key) }
            val p = Pairing(candidates)
            pairing = p
            session = PairSession(kind, brand, models, tests, common)
            auto = !oneByOne
            sentStep = null
            lastMark = null
            screen.active = kind == DeviceKind.Tv || kind == DeviceKind.Projector
            Log.i(TAG, "IR pairing ${kind.id}/$brand${if (common) " (common codes)" else ""}: ${models.size} models, ${p.tries} distinct codes")
            when (val step = p.current()) {
                is Pairing.Step.Test -> {
                    showStep(step, PairPhase.Ready)
                    host.reply(null, aimLine(kind, p.tries))
                }
                else -> noMatch()
            }
        }
    }

    private suspend fun testsFor(kind: DeviceKind, m: IrCatalog.ModelCodes): Tests? {
        val catalog = catalog ?: return null
        if (kind == DeviceKind.Ac) {
            val binary = m.ac ?: return null
            val on = AcState(power = true, mode = AcMode.Cool, tempC = 24, fan = FanSpeed.Auto, swing = false)
            val caps = codec.caps(binary) ?: return null
            val first = caps.clamp(on)
            val second = caps.clamp(first.copy(tempC = first.tempC + 1)).takeIf { it != first }
            val p1 = codec.encode(binary, first, AcKey.Power) ?: return null
            val p2 = second?.let { codec.encode(binary, it, AcKey.TempUp) }
            return Tests(
                p1, "On, ${first.mode.label.lowercase()} ${first.tempC}°", "ac_power",
                p2, second?.let { "Temperature ${it.tempC}°" }, second?.let { "ac_temp" },
                firstFace = null, secondFace = second?.let { "${it.tempC}°" }, acFirst = first, acSecond = second,
            )
        }
        val firstButton = TEST_FIRST[kind]!!.firstOrNull { catalog.pattern(m, it) != null } ?: return null
        val secondButton = TEST_SECOND[kind]!!.firstOrNull { it != firstButton && catalog.pattern(m, it) != null }
        return Tests(
            catalog.pattern(m, firstButton)!!, firstButton.label, firstButton.id,
            secondButton?.let { catalog.pattern(m, it) }, secondButton?.label, secondButton?.id,
        )
    }

    private fun showStep(step: Pairing.Step.Test, phase: PairPhase, detected: Boolean = false) {
        val s = session ?: return
        val tests = s.tests[step.model] ?: return
        _ui.update {
            it.copy(
                panel = RemotePanel.Pair(
                    kind = s.kind, brand = s.brand, stage = step.stage, attempt = step.attempt, attempts = step.attempts,
                    test = if (step.stage == 1) tests.firstLabel else tests.secondLabel ?: tests.firstLabel,
                    phase = phase, auto = auto, detected = detected,
                ),
                badge = BadgeState.Pairing,
            )
        }
    }

    /** "Start": Fixy goes through the codes by itself, watching (TV), listening (AC) or waiting for a tap (fan). */
    fun startScan() {
        val p = pairing ?: return
        val s = session ?: return
        auto = true
        job?.cancel()
        job = scope.launch {
            while (true) {
                val step = p.current() as? Pairing.Step.Test ?: return@launch
                val mark = p.mark()
                showStep(step, PairPhase.Sending)
                if (!sendStep(s, step)) {
                    pauseScan("That code didn't go out. Tap Send test to try again.")
                    return@launch
                }
                sentStep = step
                when (observe(s.kind, step)) {
                    Seen.Yes -> {
                        showStep(step, phaseAfterSend(s.kind), detected = true)
                        delay(DETECTED_PAUSE_MS)
                        sentStep = null
                        if (!advance(p.responded(), stageBefore = step.stage)) return@launch
                    }
                    Seen.Unsure -> {
                        // The camera couldn't tell (it moved, or the screen plays video): ask about this code.
                        auto = false
                        showStep(step, PairPhase.Asking)
                        host.speak("I couldn't tell from the camera. Did the ${s.kind.noun} respond?")
                        return@launch
                    }
                    Seen.No -> {
                        lastMark = mark
                        sentStep = null
                        if (!advance(p.noResponse(), stageBefore = step.stage)) return@launch
                        delay(SCAN_GAP_MS)
                    }
                }
            }
        }
    }

    /** Stop going through the codes by itself; the current code waits for "Send test". */
    fun pauseScan(line: String? = null) {
        val p = pairing ?: return
        job?.cancel()
        auto = false
        (p.current() as? Pairing.Step.Test)?.let { showStep(it, PairPhase.Ready) }
        host.speak(line ?: "Paused. Tap Send test to try the codes one at a time.")
    }

    /** One code at a time: sends the current step's code, then listens (AC) or watches (TV) and asks. */
    fun sendTest() {
        val p = pairing ?: return
        val s = session ?: return
        val step = p.current() as? Pairing.Step.Test ?: return
        auto = false
        job?.cancel()
        job = scope.launch {
            showStep(step, PairPhase.Sending)
            if (!sendStep(s, step)) {
                showStep(step, PairPhase.Ready)
                host.speak("That code didn't go out. Tap Send test to try again.")
                return@launch
            }
            sentStep = step
            if (observe(s.kind, step) == Seen.Yes) {
                showStep(step, phaseAfterSend(s.kind), detected = true)
                delay(DETECTED_PAUSE_MS)
                answer(true)
                return@launch
            }
            showStep(step, PairPhase.Asking)
            // Asked aloud once per stage; after that the card asks, so the pace stays quick.
            if (step.attempt == 1 || step.stage == 2) host.speak("Did the ${s.kind.noun} respond?")
        }
    }

    /** The answer to "Did it respond?" (buttons, voice, volume keys; the beep and the camera call it too). */
    fun answer(responded: Boolean) {
        val p = pairing ?: return
        if (session == null) return
        val before = p.current() as? Pairing.Step.Test ?: return
        // An answer wins over whatever window is still open.
        job?.cancel()
        // "It responded" a moment after Fixy already moved on: it was about the code just sent.
        if (responded && sentStep == null && lastMark != null && auto) p.restore(lastMark!!)
        val stageBefore = (p.current() as? Pairing.Step.Test)?.stage ?: before.stage
        sentStep = null
        lastMark = null
        val next = if (responded) p.responded() else p.noResponse()
        if (advance(next, stageBefore) && next is Pairing.Step.Test) {
            if (auto) {
                startScan()
            } else {
                showStep(next, PairPhase.Sending)
                // Still aiming: the next code goes out by itself after a short pause.
                job = scope.launch {
                    delay(NEXT_TEST_PAUSE_MS)
                    sendTest()
                }
            }
        }
    }

    /** Moves to [next]: pairs, reports no match, or (true) there's another code to try. */
    private fun advance(next: Pairing.Step, stageBefore: Int): Boolean {
        val s = session ?: return false
        return when (next) {
            is Pairing.Step.Test -> {
                if (next.stage == 2 && stageBefore == 1) host.speak("That one worked. One more check to be sure.")
                true
            }
            is Pairing.Step.Paired -> {
                val model = s.models[next.model]
                val tests = s.tests[next.model]
                val profile = RemoteProfile(
                    kind = s.kind, brand = s.brand, modelId = model.id, modelNumber = next.model + 1,
                    verified = next.verified,
                    acState = if (next.verified) tests?.acSecond ?: tests?.acFirst else tests?.acFirst,
                )
                scope.launch(Dispatchers.IO) { store.save(profile) }
                becomePaired(profile, announce = pairedLine(profile))
                false
            }
            Pairing.Step.NoMatch -> {
                noMatch()
                false
            }
        }
    }

    private enum class Seen { Yes, No, Unsure }

    private fun phaseAfterSend(kind: DeviceKind) = if (kind == DeviceKind.Ac) PairPhase.Listening else PairPhase.Watching

    /** After a test code: the AC's beep, the screen (TV/projector), or a window for the user's tap (fan). */
    private suspend fun observe(kind: DeviceKind, step: Pairing.Step.Test): Seen {
        showStep(step, phaseAfterSend(kind))
        return when (kind) {
            DeviceKind.Ac -> if (beep.listen()) Seen.Yes else Seen.No
            DeviceKind.Tv, DeviceKind.Projector -> when (screen.watch()) {
                ScreenProbe.Verdict.Changed -> Seen.Yes
                ScreenProbe.Verdict.Same -> Seen.No
                ScreenProbe.Verdict.Unsure -> Seen.Unsure
            }
            DeviceKind.Fan -> {
                delay(FAN_WINDOW_MS)
                Seen.No
            }
        }
    }

    private suspend fun sendStep(s: PairSession, step: Pairing.Step.Test): Boolean {
        val tests = s.tests[step.model] ?: return false
        val pattern = (if (step.stage == 1) tests.first else tests.second) ?: return false
        val key = (if (step.stage == 1) tests.firstKey else tests.secondKey) ?: tests.firstKey
        val label = (if (step.stage == 1) tests.firstLabel else tests.secondLabel) ?: tests.firstLabel
        val face = if (step.stage == 1) tests.firstFace else tests.secondFace
        val note = if (step.stage == 1) "Test code ${step.attempt} of ${step.attempts}" else "Second check"
        return transmit(pattern, "pair ${s.kind.id}/${s.brand} model ${step.model + 1} test ${step.stage}", key, label, face, true, note) != null
    }

    private fun noMatch() {
        val s = session
        val kind = s?.kind ?: return
        screen.active = false
        _ui.update { it.copy(panel = RemotePanel.NoMatch(kind, s.brand, s.common, auto), badge = it.profile?.let { BadgeState.Ready }) }
        host.reply(
            null,
            when {
                auto && kind != DeviceKind.Ac -> "I went through the ${s.brand} codes and didn't see the ${kind.noun} react. " +
                    "Let's go one code at a time, and you tell me."
                s.common -> "None of the common ${kind.noun} codes worked either. This one may not take IR remote signals."
                else -> "None of the ${s.brand} codes worked. I can try the most common ${kind.noun} codes, or you can pick another brand."
            },
        )
    }

    fun tryCommonCodes() {
        val panel = _ui.value.panel as? RemotePanel.NoMatch ?: return
        startPairing(panel.kind, panel.brand, common = true)
    }

    /** After an automatic run found nothing: the same codes again, one at a time with the user. */
    fun retryOneByOne() {
        val panel = _ui.value.panel as? RemotePanel.NoMatch ?: return
        startPairing(panel.kind, panel.brand, common = panel.triedCommon, useSaved = false, oneByOne = true)
    }

    /** Close the pairing card or the no-match card without pairing. */
    fun cancel() {
        if (_ui.value.panel is RemotePanel.Paired) {
            // Already paired: back just folds the card early.
            _ui.update { it.copy(panel = null) }
            return
        }
        val wasPairing = _ui.value.panel != null
        job?.cancel()
        pairing = null
        session = null
        sentStep = null
        lastMark = null
        pendingCommand = null
        pendingRoutine = null
        pendingGoal = null
        awaitingBrand = false
        screen.active = false
        _ui.update { it.copy(panel = null, picker = null, badge = it.profile?.let { BadgeState.Ready }) }
        if (wasPairing) host.speak("Okay, I'll leave the remote.")
    }

    private fun becomePaired(profile: RemoteProfile, announce: String) {
        pairing = null
        session = null
        sentStep = null
        lastMark = null
        missedBeeps = 0
        screen.active = false
        _ui.update { it.copy(panel = RemotePanel.Paired(profile), profile = profile, badge = BadgeState.Ready, lastSent = null) }
        host.onProfile(profile)
        host.reply(null, announce)
        val command = pendingCommand
        val routine = pendingRoutine
        val goal = pendingGoal
        pendingCommand = null
        pendingRoutine = null
        pendingGoal = null
        cardJob?.cancel()
        cardJob = scope.launch {
            delay(PAIRED_CARD_MS)
            _ui.update { if (it.panel is RemotePanel.Paired) it.copy(panel = null) else it }
            when {
                command != null -> run(command, null, byFixy = true)
                routine != null -> startAgent(routineGoal(routine), null, routine)
                goal != null -> startAgent(goal, null)
            }
        }
    }

    // ---- Control ----

    /**
     * Sends [command] to the paired device. [userText]: the spoken command (null: no card, no voice). [byFixy]:
     * Fixy pressed it (a spoken command, the agent) rather than the user on the pad; the pop-up says which.
     */
    fun run(command: RemoteCommand, userText: String?, byFixy: Boolean = false) {
        val profile = _ui.value.profile ?: return
        val model = modelFor(profile) ?: return
        job?.cancel()
        job = scope.launch { execute(profile, model, command, userText, byFixy) }
    }

    private suspend fun execute(
        profile: RemoteProfile,
        model: IrCatalog.ModelCodes,
        command: RemoteCommand,
        userText: String?,
        byFixy: Boolean,
    ): Outcome {
        val catalog = catalog ?: return Outcome(false)
        when (command) {
            is RemoteCommand.Press -> {
                val (button, pattern) = pressable(catalog, model, command.button) ?: run {
                    userText?.let { host.reply(it, "This ${profile.kind.noun} remote has no ${command.button.label.lowercase()} button.") }
                    return Outcome(false)
                }
                repeat(command.times) { i ->
                    if (i > 0) delay(REPEAT_GAP_MS)
                    transmit(pattern, "${profile.label}: ${button.label}", button.id, keyLabel(button), null, byFixy) ?: return Outcome(false)
                }
                val label = keyLabel(button) + if (command.times > 1) " ×${command.times}" else ""
                _ui.update { it.copy(lastSent = label) }
                userText?.let { host.reply(it, "$label.") }
                return Outcome(true)
            }
            is RemoteCommand.Keys -> {
                val keys = command.buttons.map { b -> catalog.pattern(model, b)?.let { b to it } }
                if (keys.any { it == null }) {
                    userText?.let { host.reply(it, "This ${profile.kind.noun} remote has no number keys.") }
                    return Outcome(false)
                }
                keys.filterNotNull().forEachIndexed { i, (button, pattern) ->
                    if (i > 0) delay(REPEAT_GAP_MS)
                    transmit(pattern, "${profile.label}: ${button.label}", button.id, button.label, null, byFixy) ?: return Outcome(false)
                }
                val number = command.buttons.joinToString("") { it.id }
                _ui.update { it.copy(lastSent = "Channel $number") }
                userText?.let { host.reply(it, "Channel $number.") }
                return Outcome(true)
            }
            is RemoteCommand.Ac -> {
                val binary = model.ac ?: return Outcome(false)
                val caps = codec.caps(binary) ?: AcCaps.ANY
                val before = profile.acState ?: AcState()
                val (next, key) = AcChange.apply(before, command.change, caps)
                val pattern = codec.encode(binary, next, key)
                if (pattern == null) {
                    userText?.let { host.reply(it, "This AC remote can't send that setting.") }
                    return Outcome(false)
                }
                val (pressKey, pressLabel, face) = acPress(command.change, next)
                transmit(pattern, "${profile.label}: ${next.describe()}", pressKey, pressLabel, face, byFixy) ?: return Outcome(false)
                val updated = profile.copy(acState = next)
                _ui.update { it.copy(profile = updated, lastSent = next.describe()) }
                host.onProfile(updated)
                val heard = beep.listen()
                missedBeeps = if (heard) 0 else missedBeeps + 1
                _ui.update { it.copy(badge = if (agentRunning) BadgeState.Working else restingBadge(it)) }
                if (userText != null) {
                    host.reply(
                        userText,
                        when {
                            heard -> "Done. ${next.describe()}."
                            missedBeeps >= 2 -> "I sent ${next.describe()}, but the AC isn't beeping back. Point the top of the phone at it."
                            else -> "Sent: ${next.describe()}."
                        },
                    )
                }
                return Outcome(true, heard)
            }
        }
    }

    /** The button's signal, or a close stand-in (a fan without "faster" still has "speed"). */
    private fun pressable(catalog: IrCatalog, model: IrCatalog.ModelCodes, button: Button): Pair<Button, IrPattern>? {
        catalog.pattern(model, button)?.let { return button to it }
        val standIn = STAND_INS[button] ?: return null
        return catalog.pattern(model, standIn)?.let { standIn to it }
    }

    // ---- The remote agent ----

    /**
     * Runs the remote agent on [goal]: each step the VLM looks at the camera picture and picks one action (press a
     * key, wait, ask the user, done); the controller checks it against this remote's keys, sends it (the pop-up
     * shows the key) and records what followed. [routine]: a known check to fall back on if the VLM can't look.
     */
    fun startAgent(goal: String, userText: String?, routine: RemoteRoutine? = null) {
        val profile = _ui.value.profile ?: return
        val model = modelFor(profile) ?: return
        job?.cancel()
        agentAnswer?.cancel()
        agentAnswer = null
        job = scope.launch {
            agentRunning = true
            try {
                agentLoop(profile, model, goal, userText, routine)
            } finally {
                agentRunning = false
                agentAnswer = null
                _ui.update { it.copy(task = null, badge = restingBadge(it)) }
            }
        }
    }

    private fun stopAgent(userText: String?) {
        job?.cancel()
        agentAnswer?.cancel()
        agentAnswer = null
        host.reply(userText, "Okay, I've stopped.")
    }

    private suspend fun agentLoop(profile: RemoteProfile, model: IrCatalog.ModelCodes, goal: String, userText: String?, routine: RemoteRoutine?) {
        val catalog = catalog ?: return
        val kind = profile.kind
        val available = if (kind == DeviceKind.Ac) emptySet() else Button.entries.filter { catalog.pattern(model, it) != null }.toSet()
        val keys = RemoteAgent.keyNames(kind, available)
        val allowPower = RemoteAgent.goalAllowsPower(goal)
        val history = mutableListOf<String>()
        host.reply(userText, "On it. I'll work the ${profile.label} remote and check as I go.")
        var current = profile
        var failures = 0
        var lastKey: String? = null
        var sameKey = 0
        for (step in 1..RemoteAgent.MAX_STEPS) {
            status(step, "looking")
            val request = RemoteAgent.prompt(current.label, kind, goal, keys, history, step, current.acState)
            val raw = runCatching { host.plan(request, PLAN_MAX_TOKENS) }.onFailure { Log.e(TAG, "Agent plan failed", it) }.getOrNull()
            Log.i(TAG, "Agent step $step: \"${raw?.trim()?.take(160)}\"")
            if (raw == null) {
                failures++
                if (step == 1 && routine != null) {
                    // The VLM can't look right now: run the known check instead.
                    Log.i(TAG, "Agent: VLM unavailable, running the $routine routine")
                    runRoutineSteps(routine, current, model)
                    return
                }
                if (failures >= MAX_FAILURES) {
                    host.reply(null, "I can't look through the camera right now, so I'll stop here.")
                    return
                }
                continue
            }
            when (val d = RemoteAgent.parse(raw, kind, available, allowPower)) {
                is RemoteAgent.Rejected -> {
                    failures++
                    history += "(your last reply was not usable: ${d.reason})"
                    if (failures >= MAX_FAILURES) {
                        host.reply(null, "I'm not sure what to press next, so I'll stop here. Tell me what you'd like to try.")
                        return
                    }
                }
                is RemoteAgent.Action.Press -> {
                    sameKey = if (d.keyName == lastKey) sameKey + 1 else 1
                    lastKey = d.keyName
                    if (sameKey > MAX_SAME_KEY) {
                        history += "(${d.keyName} pressed $MAX_SAME_KEY times in a row already: try another key, ask, or finish)"
                        continue
                    }
                    d.say?.let { host.reply(null, sentenceCase(it)) }
                    status(step, "pressing ${keyWord(d.keyName)}")
                    val outcome = execute(current, model, d.command, null, byFixy = true)
                    current = _ui.value.profile ?: current
                    val times = (d.command as? RemoteCommand.Press)?.times?.takeIf { it > 1 }?.let { " x$it" } ?: ""
                    history += "pressed ${d.keyName}$times" + when {
                        !outcome.sent -> " (it didn't go out)"
                        outcome.beep == true -> " (the AC beeped)"
                        outcome.beep == false -> " (no beep heard)"
                        else -> ""
                    }
                    status(step, "checking")
                    delay(SETTLE_MS)
                }
                is RemoteAgent.Action.Wait -> {
                    d.say?.let { host.reply(null, sentenceCase(it)) }
                    status(step, "waiting")
                    delay(d.seconds * 1000L)
                    history += "waited ${d.seconds} s"
                }
                is RemoteAgent.Action.Ask -> {
                    host.reply(null, d.question)
                    status(step, "waiting for you")
                    val pending = CompletableDeferred<String>()
                    agentAnswer = pending
                    val answer = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { pending.await() }
                    agentAnswer = null
                    if (answer == null) {
                        host.reply(null, "I'll stop here. Tell me what you see and I'll carry on.")
                        return
                    }
                    history += "asked \"${d.question}\", the user said \"$answer\""
                }
                is RemoteAgent.Action.Done -> {
                    host.reply(null, d.message)
                    return
                }
            }
        }
        host.reply(null, "I've tried ${RemoteAgent.MAX_STEPS} steps. Tell me what you see and I'll keep going.")
    }

    private fun status(step: Int, what: String) {
        _ui.update { it.copy(task = "Step $step: $what", badge = BadgeState.Working) }
    }

    /** The goal handed to the agent for a known problem, with a hint of what usually works. */
    private fun routineGoal(routine: RemoteRoutine): String = when (routine) {
        RemoteRoutine.AcCoolDown -> "The AC isn't cooling. Set cool mode at 24 degrees with the fan on high, then tell " +
            "the user to wait five minutes and check the filter if the air stays warm."
        RemoteRoutine.FindPicture -> "The screen shows no picture or no signal. Press input and look at the screen " +
            "after each press until a picture shows; if none does after a few inputs, tell the user to check the cable."
        RemoteRoutine.BringSoundBack -> "There's no sound. Try unmute or volume up, then ask the user whether they hear it."
    }

    /** The fixed version of a known check, used when the VLM can't look. */
    private suspend fun runRoutineSteps(routine: RemoteRoutine, profile: RemoteProfile, model: IrCatalog.ModelCodes) {
        when (routine) {
            RemoteRoutine.AcCoolDown -> {
                host.reply(null, "I'll set it to cool at 24 with the fan on high.")
                delay(SPEECH_ROOM_MS)
                val outcome = execute(profile, model, RemoteCommand.Ac(AcChange.Temp(24)), null, byFixy = true)
                val after = _ui.value.profile ?: profile
                execute(after, model, RemoteCommand.Ac(AcChange.SetFan(FanSpeed.High)), null, byFixy = true)
                host.reply(
                    null,
                    if (outcome.beep == true) "It's set. Give it five minutes. If the air is still warm after that, the filter may be clogged."
                    else "I sent it, but I didn't hear the AC beep. Point the top of the phone at the AC and try again.",
                )
            }
            RemoteRoutine.FindPicture -> {
                val catalog = catalog ?: return
                if (catalog.pattern(model, Button.Input) == null) {
                    host.reply(null, "This ${profile.kind.noun} remote has no input button, so I can't switch sources.")
                    return
                }
                host.reply(null, "I'll switch inputs until I see a picture. Keep the camera on the screen.")
                delay(SPEECH_ROOM_MS)
                for (i in 1..MAX_INPUT_TRIES) {
                    _ui.update { it.copy(task = "Checking input $i of $MAX_INPUT_TRIES", badge = BadgeState.Working) }
                    if (!execute(profile, model, RemoteCommand.Press(Button.Input), null, byFixy = true).sent) return
                    delay(INPUT_SETTLE_MS)
                    when (host.check("the ${profile.kind.noun} screen shows a picture or video, not a 'no signal' message, a blank screen or a menu")) {
                        true -> return host.reply(null, "There's the picture. It was on the wrong input.")
                        null -> return host.reply(null, "I switched the input. Is there a picture now?")
                        false -> Unit
                    }
                }
                host.reply(null, "I tried $MAX_INPUT_TRIES inputs and still see no picture. Check that the cable is firmly in and the source device is on.")
            }
            RemoteRoutine.BringSoundBack -> {
                host.reply(null, "I'll turn the volume up a few steps. That also unmutes most TVs.")
                delay(SPEECH_ROOM_MS)
                if (!execute(profile, model, RemoteCommand.Press(Button.VolUp, 3), null, byFixy = true).sent) return
                host.reply(null, "Can you hear it now?")
            }
        }
    }

    /** Stop controlling: the badge goes, the device stays in the paired list for next time. */
    fun release(userText: String?) {
        val profile = _ui.value.profile ?: return
        reset()
        _ui.update { it.copy(profile = null, badge = null, lastSent = null) }
        host.onProfile(null)
        host.reply(userText, "Okay, I've let go of the ${profile.label} remote.")
    }

    /** Pair the current device again (the saved model didn't respond). */
    fun pairAgain(userText: String? = null) {
        val profile = _ui.value.profile ?: return
        userText?.let { host.reply(it, "Okay, let's pair the ${profile.label} again.") }
        scope.launch(Dispatchers.IO) { store.forget(profile.kind, profile.brand) }
        startPairing(profile.kind, profile.brand, useSaved = false)
    }

    fun setPad(open: Boolean) {
        _ui.update { it.copy(padOpen = open && it.profile != null) }
    }

    // ---- Debug hooks (MainActivity) ----

    /** `--es ir "tv/LG/mute"`, `"tv/LG/#irext:463/mute"`, `"ac/LG/cool 24"`, `"ac/LG/off"`. */
    fun debugSend(spec: String) {
        val parts = spec.split('/')
        val kind = DeviceKind.of(parts.getOrNull(0)) ?: run { Log.w(TAG, "IR debug: bad device in \"$spec\""); return }
        val brand = catalog?.brand(kind, parts.getOrNull(1).orEmpty()) ?: run { Log.w(TAG, "IR debug: no brand in \"$spec\""); return }
        val pick = parts.getOrNull(2)?.takeIf { it.startsWith("#") }?.drop(1)
        val model = (pick?.let { id -> brand.models.firstOrNull { it.id == id } } ?: brand.models.firstOrNull())
            ?: run { Log.w(TAG, "IR debug: no model"); return }
        val what = parts.drop(if (pick != null) 3 else 2).joinToString("/")
        val profile = RemoteProfile(kind, brand.brand, model.id, brand.models.indexOf(model) + 1)
        scope.launch {
            val command = RemoteCommands.parse(if (kind == DeviceKind.Ac) debugAcWords(what) else "press $what", kind)
            val button = Button.of(what)
            when {
                button != null -> execute(profile, model, RemoteCommand.Press(button), null, byFixy = true)
                command is RemoteCommands.Parsed.Command -> execute(profile, model, command.command, null, byFixy = true)
                else -> Log.w(TAG, "IR debug: don't know \"$what\"")
            }
        }
    }

    /** `--es irpair "tv:LG"`: opens pairing directly (no VLM look). */
    fun debugPair(spec: String) {
        val kind = DeviceKind.of(spec.substringBefore(':')) ?: return
        val brand = catalog?.brand(kind, spec.substringAfter(':'))?.brand ?: return
        startPairing(kind, brand, useSaved = false)
    }

    /** `--es irpress "vol_up"`: shows the key pop-up as if Fixy pressed it (no send), to check the UI. */
    fun debugPress(key: String) {
        val button = Button.of(key)
        showPress(key, button?.let { keyLabel(it) } ?: key, null, byFixy = true, note = null)
        _ui.update { it.copy(sends = it.sends + 1) }
    }

    private fun debugAcWords(what: String) = when {
        what == "off" -> "turn it off"
        what == "on" -> "turn it on"
        what.startsWith("cool ") || what.startsWith("heat ") -> "set it to ${what.substringAfter(' ')} degrees"
        else -> what
    }

    // ---- Helpers ----

    /** The badge when nothing is being sent: hidden without a device, "check aim" after missed beeps. */
    private fun restingBadge(ui: RemoteUi): BadgeState? = when {
        ui.profile == null -> null
        missedBeeps >= 2 -> BadgeState.NoResponse
        else -> BadgeState.Ready
    }

    private fun modelFor(profile: RemoteProfile): IrCatalog.ModelCodes? {
        val catalog = catalog ?: return null
        return catalog.brand(profile.kind, profile.brand)?.models?.firstOrNull { it.id == profile.modelId }
            ?: catalog.common(profile.kind).firstOrNull { it.id == profile.modelId }
    }

    /** Sends one signal and shows its key under the badge. */
    private suspend fun transmit(
        pattern: IrPattern,
        what: String,
        key: String,
        label: String,
        face: String?,
        byFixy: Boolean,
        note: String? = null,
    ): Long? {
        _ui.update { it.copy(sends = it.sends + 1) }
        showPress(key, label, face, byFixy, note)
        return blaster.send(pattern, what)
    }

    /** Adds a key pop-up (or counts up the last one when the same key is pressed again right away). */
    private fun showPress(key: String, label: String, face: String?, byFixy: Boolean, note: String?) {
        val now = System.currentTimeMillis()
        var id = 0L
        var count = 1
        _ui.update { ui ->
            val last = ui.presses.lastOrNull()
            val repeat = last != null && last.key == key && last.label == label && last.byFixy == byFixy &&
                last.note == note && now - lastPressAt < SAME_KEY_MS
            val presses = if (repeat) {
                val bumped = last!!.copy(count = last.count + 1)
                id = bumped.id
                count = bumped.count
                ui.presses.dropLast(1) + bumped
            } else {
                id = ++pressSeq
                (ui.presses + KeyPress(id, key, label, face, byFixy, 1, note)).takeLast(MAX_PRESSES)
            }
            ui.copy(presses = presses)
        }
        lastPressAt = now
        val shownId = id
        val shownCount = count
        scope.launch {
            delay(PRESS_SHOW_MS)
            _ui.update { ui -> ui.copy(presses = ui.presses.filterNot { it.id == shownId && it.count == shownCount }) }
        }
    }

    private fun keyLabel(button: Button): String = if (button.isDigit) "Number ${button.id}" else button.label

    private fun keyWord(key: String): String = Button.of(key)?.let { keyLabel(it).lowercase() } ?: key.replace('_', ' ')

    /** The pop-up for an AC command: which key it looks like, its label and the text on its face. */
    private fun acPress(change: AcChange, next: AcState): Triple<String, String, String?> = when (change) {
        AcChange.On -> Triple("ac_power", "Power on", null)
        AcChange.Off -> Triple("ac_power", "Power off", null)
        AcChange.Warmer, AcChange.Cooler, is AcChange.Temp -> Triple("ac_temp", "Temperature ${next.tempC}°", "${next.tempC}°")
        is AcChange.SetMode -> Triple("ac_mode", "Mode: ${next.mode.label.lowercase()}", next.mode.label)
        is AcChange.SetFan -> Triple("ac_fan", "Fan: ${next.fan.label.lowercase()}", next.fan.label)
        AcChange.Swing -> Triple("ac_swing", if (next.swing) "Swing on" else "Swing off", null)
    }

    private fun aimLine(kind: DeviceKind, codes: Int): String = when (kind) {
        DeviceKind.Ac -> "Point the top of your phone at the AC and tap Start. I'll try $codes codes and listen for its beep."
        DeviceKind.Tv -> "Point the top of your phone at the TV, keep the screen in view, and tap Start. " +
            "I'll try $codes codes and watch for the volume bar."
        DeviceKind.Projector -> "Point the top of your phone at the projector itself and keep its picture in view, then tap Start."
        DeviceKind.Fan -> "Point the top of your phone at the fan and tap Start. Say yes the moment it reacts."
    }

    private fun pairedLine(p: RemoteProfile): String =
        if (p.verified) "Paired. I have the ${p.label} remote now. What should I do?"
        else "It responded, so I'll use this ${p.label} remote. If some buttons don't work, say \"pair again\"."

    private fun brandIn(text: String, kind: DeviceKind): String? =
        catalog?.matchBrand(kind, text)?.takeIf { it.exact }?.best

    private fun article(word: String) = if (word.first().lowercaseChar() in "aeio") "an" else "a"

    private fun sentenceCase(s: String) = s.trim().replaceFirstChar { it.uppercase() }.let { if (it.endsWith('.')) it else "$it." }

    private companion object {
        val TEST_FIRST = mapOf(
            DeviceKind.Tv to listOf(Button.VolUp, Button.Mute, Button.Power),
            DeviceKind.Projector to listOf(Button.Menu, Button.Input, Button.Power),
            DeviceKind.Fan to listOf(Button.Speed, Button.SpeedUp, Button.Power),
        )
        val TEST_SECOND = mapOf(
            DeviceKind.Tv to listOf(Button.VolDown, Button.Mute, Button.ChUp),
            DeviceKind.Projector to listOf(Button.Back, Button.Menu, Button.Input),
            DeviceKind.Fan to listOf(Button.Swing, Button.Timer, Button.Power),
        )
        val STAND_INS = mapOf(
            Button.SpeedUp to Button.Speed, Button.SpeedDown to Button.Speed, Button.Speed to Button.SpeedUp,
            Button.Home to Button.Menu, Button.Back to Button.Menu,
        )
        val CANCEL = Regex("""\b(cancel|stop pairing|never ?mind|forget it|leave it)\b""")
        val PAUSE = Regex("""\b(pause|hold on|wait|stop)\b""")
        val START = Regex("""\b(start|go|begin|ready|go ahead)\b""")
        val SEND_AGAIN = Regex("""\b(send( it)? again|try again|again|resend|send (the )?test|send it)\b""")
        val PAIR_AGAIN = Regex("""\b(pair (it |the \w+ )?again|re-?pair|pair again)\b""")
        val STOP_AGENT = Regex("""\b(stop|cancel|that's enough|enough|never ?mind|leave it)\b""")
        const val DETECTED_PAUSE_MS = 700L
        const val NEXT_TEST_PAUSE_MS = 900L
        const val SCAN_GAP_MS = 250L
        const val FAN_WINDOW_MS = 2200L
        const val PAIRED_CARD_MS = 1400L
        const val REPEAT_GAP_MS = 350L
        const val INPUT_SETTLE_MS = 3000L
        const val MAX_INPUT_TRIES = 4
        /** Lets Fixy's line start before the first send, so the voice doesn't mask the beep check. */
        const val SPEECH_ROOM_MS = 2500L
        /** Enough for one JSON action with a short reason. */
        const val PLAN_MAX_TOKENS = 70
        /** After a press, the TV needs a moment before its picture shows the result. */
        const val SETTLE_MS = 1800L
        const val MAX_FAILURES = 3
        const val MAX_SAME_KEY = 4
        const val ANSWER_TIMEOUT_MS = 30_000L
        const val MAX_PRESSES = 3
        const val PRESS_SHOW_MS = 2200L
        const val SAME_KEY_MS = 1200L
    }
}
