package com.fixlens.ir

/**
 * The on-device remote agent: Qwen-VL (through the app's VLM) plans one step at a time with the paired remote as
 * its only tool. Each step it sees the latest camera picture, the goal and what happened so far, and answers
 * with one JSON action: press a key, wait, ask the user, or finish. RemoteController runs the loop, checks every
 * action against this device's real keys, sends it and records what followed. No cloud, no agent framework:
 * the loop is a few dozen lines. This file is the pure part (prompt and parser), unit-tested.
 */
object RemoteAgent {

    /** What a reply turned into: an [Action] to run, or [Rejected]. */
    sealed interface Decision

    sealed interface Action : Decision {
        /** [say]: the model's short reason, shown and spoken so the user can follow along. */
        data class Press(val command: RemoteCommand, val say: String?, val keyName: String) : Action
        data class Wait(val seconds: Int, val say: String?) : Action
        data class Ask(val question: String) : Action
        data class Done(val message: String) : Action
    }

    /** Why a reply was not an action (logged and fed back to the model). */
    data class Rejected(val reason: String) : Decision

    const val MAX_STEPS = 8

    /** The keys the model may name for this device, in the model's words. */
    fun keyNames(kind: DeviceKind, available: Set<Button>): List<String> = when (kind) {
        DeviceKind.Ac -> AC_KEYS
        else -> Button.entries.filter { it in available }.map { it.id }
    }

    fun prompt(
        device: String,
        kind: DeviceKind,
        goal: String,
        keys: List<String>,
        history: List<String>,
        step: Int,
        acState: AcState?,
    ): String = buildString {
        append("You are Fixy, operating the user's ").append(device).append(" with its infrared remote, one step at a time. ")
        append("The picture is what the phone camera sees right now.\n")
        append("Goal: \"").append(goal.trim().take(200)).append("\"\n")
        append("Keys you can press: ").append(keys.joinToString(", ")).append('\n')
        if (kind == DeviceKind.Ac) {
            append("For \"temp\" add \"value\" (16-30); for \"mode\" a value of cool, dry, fan, auto or heat; for \"fan\" low, medium, high or auto.\n")
            acState?.let { append("Last sent to the AC: ").append(it.describe()).append('\n') }
        }
        if (history.isEmpty()) append("Nothing done yet.\n")
        else {
            append("Done so far:\n")
            history.takeLast(HISTORY_LINES).forEachIndexed { i, h -> append(i + 1).append(". ").append(h).append('\n') }
        }
        append("This is step ").append(step).append(" of at most ").append(MAX_STEPS).append(".\n")
        append("Reply with JSON only, one of:\n")
        append("{\"press\":\"<key>\",\"times\":1,\"say\":\"<why, a few words>\"}\n")
        append("{\"wait\":2,\"say\":\"<why>\"}\n")
        append("{\"ask\":\"<one short question for the user>\"}\n")
        append("{\"done\":\"<one or two short sentences for the user>\"}\n")
        append("Rules: press only listed keys. Press power only if the goal is to switch it on or off. ")
        append("Ask the user when the picture can't tell you (sound, a beep, something off camera). ")
        append("Say done as soon as the goal is reached, or when more pressing won't help.")
    }

    /** The model's reply → an action on this device, or why it can't be used. */
    fun parse(raw: String, kind: DeviceKind, available: Set<Button>, goalAllowsPower: Boolean): Decision {
        val json = Regex("""\{[^{}]*\}""").find(raw)?.value ?: return Rejected("no JSON")
        fun str(name: String) = Regex(""""$name"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)?.trim()
        fun num(name: String) = Regex(""""$name"\s*:\s*"?(\d+)"?""").find(json)?.groupValues?.get(1)?.toIntOrNull()
        val say = str("say")?.takeIf { it.isNotEmpty() }?.take(MAX_SAY)
        str("done")?.let { return Action.Done(it.ifEmpty { "Done." }.take(MAX_MESSAGE)) }
        str("ask")?.let { if (it.isNotEmpty()) return Action.Ask(it.take(MAX_MESSAGE)) }
        if (Regex(""""wait"""").containsMatchIn(json)) return Action.Wait((num("wait") ?: 2).coerceIn(1, 4), say)
        val key = str("press")?.lowercase()?.replace(' ', '_') ?: return Rejected("no action")
        val times = (num("times") ?: 1).coerceIn(1, MAX_TIMES)
        if (kind == DeviceKind.Ac) {
            val value = str("value") ?: num("value")?.toString()
            val change = acChange(key, value) ?: return Rejected("unknown AC key \"$key\"")
            if ((change == AcChange.On || change == AcChange.Off) && !goalAllowsPower) return Rejected("power not asked for")
            return Action.Press(RemoteCommand.Ac(change), say, key)
        }
        val button = Button.of(key) ?: Button.of(ALIASES[key] ?: "") ?: return Rejected("unknown key \"$key\"")
        if (button !in available) return Rejected("this remote has no \"$key\" key")
        if (button == Button.Power && !goalAllowsPower) return Rejected("power not asked for")
        return Action.Press(RemoteCommand.Press(button, if (button.isDigit) 1 else times), say, button.id)
    }

    /** Words that mean the user wants the device switched on or off. */
    fun goalAllowsPower(goal: String): Boolean =
        Regex("""\b(turn|switch|power|shut)\b.{0,20}\b(on|off|down)\b|\bpower\b|\bstandby\b""").containsMatchIn(goal.lowercase())

    private fun acChange(key: String, value: String?): AcChange? = when (key) {
        "on" -> AcChange.On
        "off" -> AcChange.Off
        "warmer", "temp_up" -> AcChange.Warmer
        "cooler", "temp_down" -> AcChange.Cooler
        "swing" -> AcChange.Swing
        "temp" -> value?.toIntOrNull()?.takeIf { it in AcState.MIN_C..AcState.MAX_C }?.let { AcChange.Temp(it) }
        "mode" -> AcMode.entries.firstOrNull { it.name.equals(value, true) }?.let { AcChange.SetMode(it) }
        "fan" -> when (value?.lowercase()) {
            null, "", "next" -> AcChange.SetFan(null)
            else -> FanSpeed.entries.firstOrNull { it.name.equals(value, true) }?.let { AcChange.SetFan(it) }
        }
        else -> null
    }

    private val AC_KEYS = listOf("on", "off", "warmer", "cooler", "temp", "mode", "fan", "swing")
    private val ALIASES = mapOf(
        "volume_up" to "vol_up", "volume_down" to "vol_down", "vol+" to "vol_up", "vol-" to "vol_down",
        "channel_up" to "ch_up", "channel_down" to "ch_down", "source" to "input", "enter" to "ok", "select" to "ok",
        "return" to "back", "exit" to "back",
    )
    private const val HISTORY_LINES = 6
    private const val MAX_TIMES = 5
    private const val MAX_SAY = 80
    private const val MAX_MESSAGE = 200
}
