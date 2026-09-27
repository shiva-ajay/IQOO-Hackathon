package com.fixlens.ir

import kotlinx.serialization.Serializable

/** The appliances Fixy can drive through the phone's IR blaster (docs/ir-remote-plan.md). */
enum class DeviceKind(val id: String, val noun: String) {
    Ac("ac", "AC"),
    Tv("tv", "TV"),
    Projector("projector", "projector"),
    Fan("fan", "fan");

    companion object {
        fun of(id: String?): DeviceKind? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/**
 * The closed set of buttons Fixy may press on a TV, projector or fan (tools/ir/build_ir_assets.py `KEYS` must
 * match). ACs don't use buttons: every AC command sends a whole state, see [AcChange].
 */
enum class Button(val id: String, val label: String) {
    Power("power", "Power"),
    Mute("mute", "Mute"),
    VolUp("vol_up", "Volume up"),
    VolDown("vol_down", "Volume down"),
    ChUp("ch_up", "Channel up"),
    ChDown("ch_down", "Channel down"),
    Input("input", "Input"),
    Menu("menu", "Menu"),
    Home("home", "Home"),
    Back("back", "Back"),
    Ok("ok", "OK"),
    Up("up", "Up"),
    Down("down", "Down"),
    Left("left", "Left"),
    Right("right", "Right"),
    Blank("blank", "Blank screen"),
    Freeze("freeze", "Freeze"),
    Speed("speed", "Speed"),
    SpeedUp("speed_up", "Faster"),
    SpeedDown("speed_down", "Slower"),
    Swing("swing", "Swing"),
    Timer("timer", "Timer"),
    Mode("mode", "Mode"),
    D0("0", "0"), D1("1", "1"), D2("2", "2"), D3("3", "3"), D4("4", "4"),
    D5("5", "5"), D6("6", "6"), D7("7", "7"), D8("8", "8"), D9("9", "9");

    val isDigit: Boolean get() = id.length == 1 && id[0].isDigit()

    companion object {
        fun of(id: String): Button? = entries.firstOrNull { it.id == id }
        fun digit(d: Int): Button = of(d.toString())!!
    }
}

/** IRext's mode order (t_ac_mode): the index is what the decoder takes. */
enum class AcMode(val label: String) { Cool("Cool"), Heat("Heat"), Auto("Auto"), Fan("Fan"), Dry("Dry") }

/** IRext's fan speed order (t_ac_wind_speed). */
enum class FanSpeed(val label: String) { Auto("Auto"), Low("Low"), Medium("Medium"), High("High") }

/** What an AC remote sends: always the whole state. */
@Serializable
data class AcState(
    val power: Boolean = true,
    val mode: AcMode = AcMode.Cool,
    val tempC: Int = 24,
    val fan: FanSpeed = FanSpeed.Auto,
    val swing: Boolean = false,
) {
    /** "Cool, 24°, fan auto" / "Off". */
    fun describe(): String =
        if (!power) "Off" else buildString {
            append(mode.label)
            if (mode != AcMode.Fan) append(", ").append(tempC).append('°')
            append(", fan ").append(fan.label.lowercase())
        }

    companion object {
        const val MIN_C = 16
        const val MAX_C = 30
    }
}

/** What one AC model accepts, per mode (from the IRext binary). */
data class AcCaps(
    val modes: Set<AcMode>,
    /** null: the mode has no temperature setting. */
    val temps: Map<AcMode, IntRange?>,
    val fans: Map<AcMode, Set<FanSpeed>>,
    val swing: Map<AcMode, Boolean>,
) {
    /** The nearest state this model can send: a supported mode, temperature and fan speed. */
    fun clamp(s: AcState): AcState {
        val mode = if (s.mode in modes || modes.isEmpty()) s.mode else modes.first()
        val range = temps[mode]
        val temp = range?.let { s.tempC.coerceIn(it.first, it.last) } ?: s.tempC.coerceIn(AcState.MIN_C, AcState.MAX_C)
        val fanSet = fans[mode].orEmpty()
        val fan = if (fanSet.isEmpty() || s.fan in fanSet) s.fan else fanSet.first()
        return s.copy(mode = mode, tempC = temp, fan = fan, swing = s.swing && swing[mode] == true)
    }

    companion object {
        /** When a model's capabilities can't be read: everything allowed. */
        val ANY = AcCaps(
            AcMode.entries.toSet(),
            AcMode.entries.associateWith { AcState.MIN_C..AcState.MAX_C },
            AcMode.entries.associateWith { FanSpeed.entries.toSet() },
            AcMode.entries.associateWith { true },
        )
    }
}

/** IRext AC key codes (KEY_AC_*): which "button" the new state is sent as. Some protocols encode it. */
enum class AcKey(val code: Int) { Power(0), Mode(1), TempUp(2), TempDown(3), Fan(9), Swing(10) }

/** One spoken or tapped AC command, applied to the last sent state. */
sealed interface AcChange {
    data object On : AcChange
    data object Off : AcChange
    data object Warmer : AcChange
    data object Cooler : AcChange
    data class Temp(val c: Int) : AcChange
    data class SetMode(val mode: AcMode) : AcChange
    /** null: the next speed. */
    data class SetFan(val fan: FanSpeed?) : AcChange
    data object Swing : AcChange

    companion object {
        /** The state after [change] and the key it's sent as, clamped to what the model accepts. */
        fun apply(state: AcState, change: AcChange, caps: AcCaps): Pair<AcState, AcKey> {
            val on = state.copy(power = true)
            val (next, key) = when (change) {
                On -> on to AcKey.Power
                Off -> state.copy(power = false) to AcKey.Power
                Warmer -> on.copy(tempC = state.tempC + 1) to AcKey.TempUp
                Cooler -> on.copy(tempC = state.tempC - 1) to AcKey.TempDown
                is Temp -> on.copy(tempC = change.c) to if (change.c >= state.tempC) AcKey.TempUp else AcKey.TempDown
                is SetMode -> on.copy(mode = change.mode) to AcKey.Mode
                is SetFan -> {
                    val options = caps.fans[state.mode].orEmpty().ifEmpty { FanSpeed.entries.toSet() }.sortedBy { it.ordinal }
                    val fan = change.fan ?: options[(options.indexOf(state.fan) + 1).mod(options.size)]
                    on.copy(fan = fan) to AcKey.Fan
                }
                Swing -> on.copy(swing = !state.swing) to AcKey.Swing
            }
            return caps.clamp(next) to key
        }
    }
}

/** A command for the paired device. */
sealed interface RemoteCommand {
    data class Press(val button: Button, val times: Int = 1) : RemoteCommand
    /** Keys pressed in turn, e.g. a channel number: 1, 0, 5. */
    data class Keys(val buttons: List<Button>) : RemoteCommand
    data class Ac(val change: AcChange) : RemoteCommand
}

/**
 * One key the remote just sent, for the pop-up under the badge. [key]: a [Button] id or an AC key ("ac_power",
 * "ac_temp", "ac_mode", "ac_fan", "ac_swing") that picks the key face; [face] is text for keys shown as text
 * ("24°", "Cool"). [count] grows when the same key is pressed again right away.
 */
data class KeyPress(
    val id: Long,
    val key: String,
    val label: String,
    val face: String? = null,
    val byFixy: Boolean = true,
    val count: Int = 1,
    /** "Test code 3 of 52" while pairing. */
    val note: String? = null,
)

/** A ready-to-send IR signal: carrier and µs timings, mark first. */
class IrPattern(val carrierHz: Int, val timings: IntArray) {
    val durationUs: Long get() = timings.fold(0L) { acc, v -> acc + v }

    /** Content key, to tell apart codes that really differ (pairing tries each distinct code once). */
    val key: String by lazy { carrierHz.toString() + ":" + timings.contentHashCode() + ":" + timings.size }

    /** Null when Android's ConsumerIrManager would refuse it. */
    fun validOrNull(): IrPattern? {
        if (timings.isEmpty() || timings.any { it <= 0 }) return null
        if (durationUs > MAX_DURATION_US) return null
        return if (timings.size % 2 == 0) IrPattern(carrierHz, timings.copyOf(timings.size - 1)) else this
    }

    companion object {
        /** ConsumerIrManager.transmit refuses patterns over 2 s. */
        const val MAX_DURATION_US = 2_000_000L
    }
}

/** The device Fixy holds the remote for. Stored with the session and in the phone's paired-devices list. */
@Serializable
data class RemoteProfile(
    val kind: DeviceKind,
    val brand: String,
    val modelId: String,
    /** "Model 3" as shown while pairing (1-based position in the brand's list). */
    val modelNumber: Int,
    /** False when only the first test got a response (the second one never did). */
    val verified: Boolean = true,
    /** AC only: what was last sent (IR is one-way: the AC's real state is unknown until the camera reads it). */
    val acState: AcState? = null,
    /** The user's own name for it on the home remote list ("Bedroom AC"), or null. */
    val name: String? = null,
) {
    val label: String get() = "$brand ${kind.noun}"

    /** What the saved-remotes list shows: the user's name, else "LG TV". */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: label
}
