package com.fixlens.ir

/**
 * Plain word rules for what the user says about a remote: take/stop the remote, a command for the paired device
 * ("set it to 24", "volume up by 3", "mute"), a short check routine ("the AC isn't cooling"), or a yes/no while
 * pairing. No model call, so a command goes out in milliseconds. Pure, unit-tested.
 *
 * Bare guide words ("back", "next", "done", "repeat", "stop") are never taken here: they belong to the repair guide.
 */
object RemoteCommands {

    sealed interface Parsed {
        /**
         * "Take the remote", "control the TV". Once paired, run [then] ("turn on the AC") or [routine]
         * ("take the remote and check the TV").
         */
        data class Take(
            val kind: DeviceKind?,
            val then: RemoteCommand? = null,
            val routine: RemoteRoutine? = null,
            /** An open-ended goal for the remote agent once paired ("take the remote and find the laptop input"). */
            val goal: String? = null,
        ) : Parsed
        data object Release : Parsed
        data class Command(val command: RemoteCommand) : Parsed
        data class Routine(val routine: RemoteRoutine) : Parsed
        /** A goal that needs looking and deciding (RemoteAgent): "switch to HDMI 2", "fix the sound". */
        data class Agent(val goal: String) : Parsed
    }

    enum class Answer { Yes, No }

    /**
     * [paired]: the device Fixy holds the remote for (commands are only read for it), or null. Returns null for
     * anything that isn't about the remote, so it goes on to the guide and the VLM as usual.
     */
    fun parse(text: String, paired: DeviceKind?): Parsed? {
        val t = clean(text)
        if (t.isEmpty()) return null
        if (RELEASE.containsMatchIn(t)) return if (paired != null) Parsed.Release else null
        // With the AC held, "fan" alone means the AC's fan ("low fan"); a fan appliance is named as one.
        val named = deviceIn(t).let { if (it == DeviceKind.Fan && paired == DeviceKind.Ac && !FAN_APPLIANCE.containsMatchIn(t)) DeviceKind.Ac else it }
        if (TAKE.containsMatchIn(t)) {
            val kind = named ?: paired
            val routine = kind?.let { routine(t, it) }
            val goal = text.trim().takeIf { routine == null && AGENT.containsMatchIn(t) }
            return Parsed.Take(kind, kind?.let { command(t, it) }, routine, goal)
        }
        // Commands for the paired device, or for another one named outright (then it has to be taken first).
        val kind = named ?: paired ?: return null
        routine(t, kind)?.let { return if (kind == paired) Parsed.Routine(it) else null }
        if (kind == paired && (kind == DeviceKind.Tv || kind == DeviceKind.Projector)) {
            digits(t)?.let { return Parsed.Command(RemoteCommand.Keys(it)) }
        }
        // Goals that need looking and deciding go to the agent; plain commands stay on the fast path below.
        if (kind == paired && AGENT.containsMatchIn(t)) return Parsed.Agent(text.trim())
        // "How do I turn off the TV?" is a question for the guide, not a command. So is a sentence that only
        // mentions a command ("it's noisy when I turn on the AC"): a command is asked for ("turn on the AC",
        // "can you…"), or short ("volume up", "low fan").
        if (QUESTION.containsMatchIn(t)) return null
        if (!IMPERATIVE.containsMatchIn(t) && t.split(' ').size > MAX_TERSE_WORDS) return null
        val command = command(t, kind) ?: return null
        return if (kind == paired) Parsed.Command(command) else Parsed.Take(kind, command)
    }

    /**
     * "My TV remote isn't working", "the TV doesn't respond when I press mute on the remote": the user's own remote
     * seems broken, so Fixy tests the device with its remote straight away.
     */
    fun remoteBroken(text: String): Boolean = clean(text).let { t ->
        MY_REMOTE.containsMatchIn(t) && REMOTE_FAILS.containsMatchIn(t) && !FIXYS_REMOTE.containsMatchIn(t)
    }

    /**
     * The key the user says doesn't work on their remote ("mute doesn't work" → mute), so Fixy tests that very key.
     * Null when no key is named.
     */
    fun brokenKey(text: String): Button? {
        val t = clean(text)
        return when {
            Regex("""\b(un)?mute\b""").containsMatchIn(t) -> Button.Mute
            Regex("""\b(volume down|lower the volume|decrease the volume|reduce the volume|quieter)\b""").containsMatchIn(t) -> Button.VolDown
            Regex("""\b(volume|louder|sound up)\b""").containsMatchIn(t) -> Button.VolUp
            Regex("""\b(channel down|previous channel)\b""").containsMatchIn(t) -> Button.ChDown
            Regex("""\bchannels?\b""").containsMatchIn(t) -> Button.ChUp
            Regex("""\b(input|source|hdmi)\b""").containsMatchIn(t) -> Button.Input
            Regex("""\bmenu\b""").containsMatchIn(t) -> Button.Menu
            Regex("""\b(power|turn (it |the \w+ )?(on|off)|switch (it |the \w+ )?(on|off))\b""").containsMatchIn(t) -> Button.Power
            else -> null
        }
    }

    /** "Test the TV with your remote", "is it the remote or the TV?": check the device with Fixy's own remote. */
    fun wantsRemoteTest(text: String): Boolean = TEST_WITH_REMOTE.containsMatchIn(clean(text))

    /**
     * While a "remote not working" guide is on: the user says the fix didn't help or asks for a diagnosis, so it's
     * time to test the device with Fixy's own remote.
     */
    fun stillBroken(text: String): Boolean = clean(text).let {
        STILL_BROKEN.containsMatchIn(it) || TEST_WITH_REMOTE.containsMatchIn(it) || DIAGNOSE_IT.containsMatchIn(it)
    }

    /** "Same TV" (true) or "a new one" (false) to "Is it the same TV as before?"; null if neither. */
    fun sameDevice(text: String): Boolean? {
        val t = clean(text)
        return when {
            DIFFERENT.containsMatchIn(t) -> false
            SAME.containsMatchIn(t) || YES_START.containsMatchIn(t) -> true
            NO.containsMatchIn(t) -> false
            else -> null
        }
    }

    /** "I'm pointing at it", "ready", "yes", "go": the phone is aimed. */
    fun ready(text: String): Boolean = clean(text).let { READY.containsMatchIn(it) || YES_START.containsMatchIn(it) }

    /** A yes/no to "Did it respond?". */
    fun answer(text: String): Answer? {
        val t = clean(text)
        return when {
            YES_START.containsMatchIn(t) -> Answer.Yes
            NO.containsMatchIn(t) -> Answer.No
            YES.containsMatchIn(t) -> Answer.Yes
            else -> null
        }
    }

    fun deviceIn(text: String): DeviceKind? {
        val t = clean(text)
        val found = DEVICE_WORDS.filter { (_, re) -> re.containsMatchIn(t) }.map { it.first }
        return found.singleOrNull() ?: found.firstOrNull()
    }

    private fun routine(t: String, kind: DeviceKind): RemoteRoutine? = when (kind) {
        DeviceKind.Ac -> if (AC_NOT_COOLING.containsMatchIn(t) || DIAGNOSE.containsMatchIn(t)) RemoteRoutine.AcCoolDown else null
        DeviceKind.Tv, DeviceKind.Projector -> when {
            NO_PICTURE.containsMatchIn(t) || DIAGNOSE.containsMatchIn(t) -> RemoteRoutine.FindPicture
            kind == DeviceKind.Tv && NO_SOUND.containsMatchIn(t) -> RemoteRoutine.BringSoundBack
            else -> null
        }
        DeviceKind.Fan -> null
    }

    private fun command(t: String, kind: DeviceKind): RemoteCommand? = when (kind) {
        DeviceKind.Ac -> acChange(t)?.let { RemoteCommand.Ac(it) }
        DeviceKind.Tv, DeviceKind.Projector -> screenButton(t, kind)
        DeviceKind.Fan -> fanButton(t)
    }

    private fun acChange(t: String): AcChange? {
        temperature(t)?.let { return AcChange.Temp(it) }
        if (OFF.containsMatchIn(t)) return AcChange.Off
        // "Turn it on in cool mode" is a mode change (which also switches it on).
        MODE.find(t)?.let { m -> return AcChange.SetMode(modeOf(m.groupValues[1] + m.groupValues[2])) }
        FAN_LEVEL.find(t)?.let { m -> return AcChange.SetFan(fanOf(m.groupValues[1] + m.groupValues[2])) }
        return when {
            WARMER.containsMatchIn(t) -> AcChange.Warmer
            COOLER.containsMatchIn(t) -> AcChange.Cooler
            // "Turn on the swing" is about the swing, not the power.
            SWING.containsMatchIn(t) -> AcChange.Swing
            ON.containsMatchIn(t) -> AcChange.On
            FAN_NEXT.containsMatchIn(t) -> AcChange.SetFan(null)
            else -> null
        }
    }

    private fun screenButton(t: String, kind: DeviceKind): RemoteCommand? {
        val times = times(t)
        val button = when {
            UNMUTE_OR_MUTE.containsMatchIn(t) -> Button.Mute
            VOL_UP.containsMatchIn(t) -> Button.VolUp
            VOL_DOWN.containsMatchIn(t) -> Button.VolDown
            CH_UP.containsMatchIn(t) -> Button.ChUp
            CH_DOWN.containsMatchIn(t) -> Button.ChDown
            INPUT.containsMatchIn(t) -> Button.Input
            ON.containsMatchIn(t) || OFF.containsMatchIn(t) -> Button.Power
            kind == DeviceKind.Projector && BLANK.containsMatchIn(t) -> Button.Blank
            kind == DeviceKind.Projector && FREEZE.containsMatchIn(t) -> Button.Freeze
            else -> PRESS.find(t)?.let { navButton(it.groupValues[1]) }
        } ?: return null
        val repeatable = button in setOf(Button.VolUp, Button.VolDown, Button.ChUp, Button.ChDown, Button.Input,
            Button.Up, Button.Down, Button.Left, Button.Right)
        return RemoteCommand.Press(button, if (repeatable) times else 1)
    }

    private fun fanButton(t: String): RemoteCommand? {
        val button = when {
            ON.containsMatchIn(t) || OFF.containsMatchIn(t) -> Button.Power
            FASTER.containsMatchIn(t) -> Button.SpeedUp
            SLOWER.containsMatchIn(t) -> Button.SpeedDown
            FAN_SPEED.containsMatchIn(t) -> Button.Speed
            SWING.containsMatchIn(t) -> Button.Swing
            TIMER.containsMatchIn(t) -> Button.Timer
            else -> null
        } ?: return null
        return RemoteCommand.Press(button)
    }

    /** "channel 105", "go to channel 7", "press 3": the number's keys in turn (up to 4 digits). */
    fun digits(t: String): List<Button>? {
        val number = DIGITS.find(t)?.groupValues?.get(1) ?: return null
        return number.map { Button.digit(it - '0') }
    }

    /** "set it to 24", "24 degrees", "twenty four degrees", "make it 22". Only 16..30. */
    fun temperature(t: String): Int? {
        val digits = TEMP_NUMBER.find(t)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull() }
        val value = digits ?: WORD_TEMP.find(t)?.let { wordNumber(it.value) }
        return value?.takeIf { it in AcState.MIN_C..AcState.MAX_C }
    }

    private fun times(t: String): Int {
        val m = TIMES.find(t) ?: return 1
        val raw = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return 1
        return (raw.toIntOrNull() ?: SMALL_NUMBERS[raw] ?: 1).coerceIn(1, MAX_REPEAT)
    }

    private fun navButton(word: String): Button? = when (word) {
        "ok", "okay", "enter", "select" -> Button.Ok
        "up" -> Button.Up
        "down" -> Button.Down
        "left" -> Button.Left
        "right" -> Button.Right
        "back", "return" -> Button.Back
        "home" -> Button.Home
        "menu" -> Button.Menu
        else -> null
    }

    private fun modeOf(word: String): AcMode = when {
        word.startsWith("heat") || word.startsWith("warm") -> AcMode.Heat
        word.startsWith("dry") || word.startsWith("dehum") -> AcMode.Dry
        word.startsWith("fan") -> AcMode.Fan
        word.startsWith("auto") -> AcMode.Auto
        else -> AcMode.Cool
    }

    private fun fanOf(word: String): FanSpeed = when {
        word.startsWith("hi") || word.startsWith("max") || word.startsWith("full") -> FanSpeed.High
        word.startsWith("med") || word.startsWith("mid") -> FanSpeed.Medium
        word.startsWith("lo") || word.startsWith("min") -> FanSpeed.Low
        else -> FanSpeed.Auto
    }

    private fun wordNumber(s: String): Int? {
        val parts = s.split(Regex("""[\s-]+""")).filter { it.isNotEmpty() }
        return when (parts.size) {
            1 -> TEENS_AND_TENS[parts[0]]
            2 -> if (parts[0] == "twenty") UNITS[parts[1]]?.let { 20 + it } else null
            else -> null
        }
    }

    private fun clean(text: String) = text.lowercase().replace('’', '\'').replace(Regex("""[^a-z0-9'°\s-]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private const val MAX_REPEAT = 10
    private val UNITS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9)
    private val TEENS_AND_TENS = mapOf("sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30)
    private val SMALL_NUMBERS = UNITS + mapOf("ten" to 10, "a couple" to 2, "couple" to 2)

    private val DEVICE_WORDS = listOf(
        DeviceKind.Ac to Regex("""\b(ac|a c|a\.c|air ?con\w*|aircon|split ac|window ac|cooler unit)\b"""),
        DeviceKind.Tv to Regex("""\b(tv|t v|television|telly|smart tv)\b"""),
        DeviceKind.Projector to Regex("""\b(projector|beamer)\b"""),
        DeviceKind.Fan to Regex("""\b(ceiling fan|table fan|pedestal fan|tower fan|the fan|my fan|fan)\b(?! speed)(?! mode)"""),
    )

    private val MY_REMOTE = Regex("""\b(remote|remote control|clicker)\b""")
    private val REMOTE_FAILS = Regex(
        """\b(not|isn't|is not|doesn't|does not|don't|won't|can't|cannot|stopped|no longer)\b.{0,20}\b(work|working|respond|responding|react|change|changing|do anything|doing anything)\w*\b|""" +
            """\b(does nothing|nothing happens|dead|no response|not responding|broken)\b""",
    )
    /** "Your remote isn't working" is about Fixy's remote (pairing), not the user's. */
    private val FIXYS_REMOTE = Regex("""\b(your|the phone'?s?|the digital|the app'?s?|fixy'?s?) remote\b""")
    private val TEST_WITH_REMOTE = Regex(
        """\b(test|check|try)\b.{0,25}\b(with|using) (your|the phone'?s?|the digital|the app'?s?|fixy'?s?) remote\b|""" +
            """\buse (your|the digital|the phone'?s?) remote\b|\bis it the remote or the (tv|ac|projector|fan)\b|""" +
            """\bis it the (tv|ac|projector|fan) or the remote\b""",
    )
    /** Only while a "remote not working" guide is on: then these mean "find out what's wrong". */
    private val DIAGNOSE_IT = Regex("""\b(diagnose|troubleshoot|figure (it )?out|what'?s wrong|find the (problem|issue)|check it)\b""")
    private val STILL_BROKEN = Regex(
        """\b(still|again)\b.{0,20}\b(not|isn't|doesn't|won't|no|nothing|same)\b|\b(didn't|did not|doesn't|does not) (help|work|fix)\b|""" +
            """\bnot working\b|\bsame problem\b|\balready (tried|changed|put)\b|\bnew batteries\b""",
    )
    private val SAME = Regex("""\b(same|that one|the one before|this one|it is|it's the same)\b""")
    private val DIFFERENT = Regex("""\b(new|different|another|other one|not the same)\b""")
    private val READY = Regex("""\b(ready|pointed|pointing|aimed|aiming|go ahead|go|done|ok|okay|press it|do it|now)\b""")
    private val DIGITS = Regex("""\b(?:channel|press|number|key|go to channel|put on channel)\s+(\d{1,4})\b""")
    private val AGENT = Regex(
        """\b(check|diagnose|troubleshoot|fix|figure out|find|look for|search for|switch to|change to|go to|get (it|the|me)|""" +
            """make (it|the) work|help me|problem|not working|isn't working|doesn't work|stopped working|""" +
            """no (sound|picture|signal|audio|image)|black screen|blank screen|can't hear|set (it )?up|open (the )?(settings|youtube|netflix|apps?))\b""",
    )
    private val FAN_APPLIANCE = Regex("""\b(ceiling|table|pedestal|tower|wall|stand) fan\b""")
    private val TAKE = Regex(
        """\b(take|use|grab|get|have|access)\b.{0,20}\bremote\b|\bremote (control )?access\b|\btake control\b|""" +
            """\b(control|pair( up)? with|connect to|operate)\b( the| my| this| that)?( [a-z]+){0,2} (ac|a c|air ?con\w*|tv|television|projector|fan)\b|""" +
            """\bpair (the|my|this)( [a-z]+){0,2} (ac|tv|projector|fan)\b""",
    )
    private val RELEASE = Regex(
        """\b(stop|quit|end|release|drop|let go of|disconnect|leave)\b.{0,20}\b(remote|controlling|control)\b|""" +
            """\bgive (me )?(back )?the remote\b""",
    )
    private const val MAX_TERSE_WORDS = 6
    private val IMPERATIVE = Regex(
        """^((please|fixy|hey fixy|okay|ok|now|and|so|can you|could you|would you|will you|i want you to|i'd like you to)\s+)*""" +
            """(turn|switch|set|put|make|power|shut|increase|decrease|raise|lower|reduce|mute|unmute|change|cycle|press|hit|""" +
            """push|go|start|give|bring|speed|slow)\b""",
    )
    private val QUESTION = Regex("""\b(how|why|what|where|when|which|should|shall i|won't|wont|can't|cannot|doesn't|isn't|is it|does it)\b""")
    private val YES_START = Regex("""^(yes|yeah|yep|yup|ya|yes it|it did)\b""")
    private val NO = Regex(
        """\b(no|nope|nah|nothing( happened)?|didn't|did not|doesn't|does not|not working|no response|""" +
            """no change|try (the )?next|try another|next( one)?)\b""",
    )
    private val YES = Regex(
        """\b(yes|yeah|yep|yup|it (worked|works|responded|beeped|changed|turned)|worked|responded|beeped|""" +
            """it's on|that's it|correct|perfect|got it)\b""",
    )

    private val ON = Regex("""\b(turn|switch|power|put)( it| the \w+( \w+)?)? on\b|\bpower on\b|\bswitch it on\b""")
    private val OFF = Regex("""\b(turn|switch|power|shut)( it| the \w+( \w+)?)? off\b|\bpower off\b|\bshut (it )?down\b""")
    private val TEMP_NUMBER = Regex(
        """\b(?:to|at|set(?: it)?(?: to)?|make it|temperature(?: to)?|temp(?: to)?)\s*(\d{2})\b|\b(\d{2})\s*(?:degrees?|deg|°c?|celsius)\b""",
    )
    private val WORD_TEMP = Regex("""\b(twenty[\s-](one|two|three|four|five|six|seven|eight|nine)|sixteen|seventeen|eighteen|nineteen|twenty|thirty)\b(?=\s*(degrees?|°|celsius))""")
    private val WARMER = Regex(
        """\b(warmer|hotter|less cold|too cold|increase (the )?temp\w*|raise (the )?temp\w*|temp\w* up|turn (it|the temp\w*) up|higher temp\w*)\b""",
    )
    private val COOLER = Regex(
        """\b(cooler|colder|more cold|too warm|decrease (the )?temp\w*|lower (the )?temp\w*|reduce (the )?temp\w*|temp\w* down|turn (it|the temp\w*) down|lower temp\w*)\b""",
    )
    private val MODE = Regex("""\b(cool|cooling|heat|heating|warm|dry|dehumidif\w*|fan|auto)\s+mode\b|\bswitch (?:it )?to (?:the )?(cool|heat|dry|fan|auto)\w*\b""")
    private val FAN_LEVEL = Regex("""\bfan(?: speed)?(?: to)?(?: on)? (high|max\w*|full|medium|mid|low|min\w*|auto)\b|\b(high|medium|low) fan\b""")
    private val FAN_NEXT = Regex("""\b(fan faster|faster fan|change the fan|fan speed)\b""")
    private val SWING = Regex("""\b(swing|oscillat\w*|rotate|rotation|louvers?|flaps?)\b""")

    private val UNMUTE_OR_MUTE = Regex("""\b(un)?mute\b""")
    private val VOL_UP = Regex("""\b(volume up|turn (it |the volume |the sound )?up|louder|increase (the )?(volume|sound)|raise (the )?volume|more volume)\b""")
    private val VOL_DOWN = Regex("""\b(volume down|turn (it |the volume |the sound )?down|quieter|softer|lower (the )?volume|decrease (the )?(volume|sound)|reduce (the )?volume|less volume)\b""")
    private val CH_UP = Regex("""\b(channel up|next channel)\b""")
    private val CH_DOWN = Regex("""\b(channel down|previous channel|last channel)\b""")
    private val INPUT = Regex("""\b(change|switch|cycle|next|toggle)( the)? (input|source)\b|\bhdmi\b|\b(input|source) (button|key)\b""")
    private val BLANK = Regex("""\bblank (the )?(screen|picture)\b|\bhide (the )?(screen|picture)\b""")
    private val FREEZE = Regex("""\bfreeze\b""")
    private val PRESS = Regex("""\b(?:press|hit|push|tap|click|go)\s+(?:the\s+)?(ok|okay|enter|select|up|down|left|right|back|return|home|menu)\b""")
    private val TIMES = Regex("""\bby (\d+|two|three|four|five|six|seven|eight|nine|ten|a couple)\b|\b(\d+|two|three|four|five|six|seven|eight|nine|ten) (times|steps|notches|clicks|levels)\b""")

    private val FASTER = Regex("""\b(faster|speed (it )?up|increase (the )?speed|more speed|higher speed)\b""")
    private val SLOWER = Regex("""\b(slower|slow (it )?down|decrease (the )?speed|reduce (the )?speed|less speed|lower speed)\b""")
    private val FAN_SPEED = Regex("""\b(change (the )?speed|fan speed|speed button)\b""")
    private val TIMER = Regex("""\btimer\b""")

    private val DIAGNOSE = Regex("""\b(diagnose|check|test|troubleshoot|inspect)\b( the| my| this)? (ac|a c|air ?con\w*|tv|television|projector)\b""")
    private val AC_NOT_COOLING = Regex(
        """\b(not|isn't|is not|no longer|doesn't|does not|won't)( \w+)? (cool\w*|cold)\b|\bwarm air\b|\b(it's|it is|too) hot\b|\bnot cold enough\b""",
    )
    private val NO_PICTURE = Regex(
        """\bno (picture|signal|image|display|video)\b|\b(black|blank|dark) screen\b|\bscreen is (black|blank|dark)\b|""" +
            """\bnothing on (the )?(screen|tv)\b|\bwrong (input|source)\b""",
    )
    private val NO_SOUND = Regex("""\bno (sound|audio|volume)\b|\bcan't hear\b|\bcannot hear\b|\bsound (isn't|is not|not) working\b|\bmuted\b""")
}

/** Short checks Fixy runs itself with the remote and the camera (see RemoteController). */
enum class RemoteRoutine { AcCoolDown, FindPicture, BringSoundBack }
