package com.fixlens.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.fixlens.app.TAG
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * The phone's IR blaster (ConsumerIrManager). Sends run one at a time on the `fixlens-ir` thread: `transmit`
 * blocks for the length of the signal, and the IRext AC decoder ([AcCodec]) shares this thread because it keeps
 * global state. [fake] (debug) logs instead of sending, for testing the flow on a phone without IR.
 */
class IrBlaster(context: Context) {
    private val ir: ConsumerIrManager? = context.getSystemService(ConsumerIrManager::class.java)
    private val ranges: List<IntRange> = runCatching {
        ir?.carrierFrequencies.orEmpty().map { it.minFrequency..it.maxFrequency }
    }.getOrDefault(emptyList())

    /** The single IR thread; [AcCodec] runs here too. */
    val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "fixlens-ir") }.asCoroutineDispatcher()

    @Volatile var fake = false

    val hasEmitter: Boolean = runCatching { ir?.hasIrEmitter() == true }.getOrDefault(false)
    val available: Boolean get() = hasEmitter || fake

    fun describe(): String =
        "IR emitter ${if (hasEmitter) "present" else "absent"}${if (fake) " (fake sends on)" else ""}, carriers " +
            ranges.joinToString { "${it.first}-${it.last} Hz" }.ifEmpty { "unknown" }

    /** Sends [pattern] and returns how long the send took (ms), or null if it couldn't be sent. */
    suspend fun send(pattern: IrPattern, what: String): Long? = withContext(dispatcher) {
        val valid = pattern.validOrNull()
        if (valid == null) {
            Log.w(TAG, "IR $what: pattern refused (${pattern.timings.size} values, ${pattern.durationUs} us)")
            return@withContext null
        }
        val carrier = carrierFor(valid.carrierHz)
        val start = System.currentTimeMillis()
        try {
            if (hasEmitter && !fake) ir!!.transmit(carrier, valid.timings)
            else Thread.sleep(valid.durationUs / 1000)
        } catch (e: Exception) {
            Log.e(TAG, "IR $what: transmit failed", e)
            return@withContext null
        }
        val ms = System.currentTimeMillis() - start
        Log.i(TAG, "IR $what: ${if (fake || !hasEmitter) "fake " else ""}sent ${valid.timings.size} values, " +
            "${valid.durationUs / 1000} ms signal at $carrier Hz in $ms ms")
        ms
    }

    /** The wanted carrier, or the nearest one this phone's emitter supports. */
    private fun carrierFor(hz: Int): Int {
        if (ranges.isEmpty() || ranges.any { hz in it }) return hz
        return ranges.map { r -> hz.coerceIn(r.first, r.last) }.minBy { kotlin.math.abs(it - hz) }
    }
}

/** JNI to the vendored IRext decoder (app/src/main/cpp/fixlens_ir.cpp). Only [AcCodec] calls it. */
internal object IrextNative {
    init {
        System.loadLibrary("fixlensir")
    }

    external fun open(binary: ByteArray): Boolean
    external fun close()
    external fun caps(): IntArray
    external fun encode(power: Int, mode: Int, temp: Int, wind: Int, swing: Int, key: Int): IntArray
}

/**
 * Builds AC signals from an IRext binary. Every call runs on [IrBlaster.dispatcher] (IRext is single-threaded);
 * the last opened binary stays open, so sending to the paired AC doesn't reopen it each time.
 */
class AcCodec(private val blaster: IrBlaster, private val readBinary: (String) -> ByteArray) {
    private var openName: String? = null
    private var openCaps: AcCaps? = null

    suspend fun caps(binary: String): AcCaps? = withContext(blaster.dispatcher) { if (ensureOpen(binary)) openCaps else null }

    /** The signal for [state] sent as [key], or null if this model can't build it. */
    suspend fun encode(binary: String, state: AcState, key: AcKey): IrPattern? = withContext(blaster.dispatcher) {
        if (!ensureOpen(binary)) return@withContext null
        val timings = runCatching {
            IrextNative.encode(
                power = if (state.power) 0 else 1,
                mode = state.mode.ordinal,
                temp = (state.tempC - AcState.MIN_C).coerceIn(0, AcState.MAX_C - AcState.MIN_C),
                wind = state.fan.ordinal,
                swing = if (state.swing) 0 else 1,
                key = key.code,
            )
        }.onFailure { Log.e(TAG, "IR: AC encode failed", it) }.getOrNull() ?: return@withContext null
        if (timings.size < 8) null else IrPattern(CARRIER_HZ, timings).validOrNull()
    }

    private fun ensureOpen(binary: String): Boolean {
        if (openName == binary) return openCaps != null
        openName = binary
        openCaps = null
        val bytes = runCatching { readBinary(binary) }.onFailure { Log.e(TAG, "IR: AC file $binary missing", it) }.getOrNull()
            ?: return false
        // A missing native library (UnsatisfiedLinkError) must fail this AC, not the app.
        val caps = runCatching { if (IrextNative.open(bytes)) parseCaps(IrextNative.caps()) else null }
            .onFailure { Log.e(TAG, "IR: AC decoder unavailable", it) }.getOrNull()
        if (caps == null) Log.w(TAG, "IR: AC file $binary did not open")
        openCaps = caps
        return caps != null
    }

    private fun parseCaps(raw: IntArray): AcCaps {
        if (raw.size < 21 || raw[0] == 0) return AcCaps.ANY
        val modes = AcMode.entries.filter { raw[0] and (1 shl it.ordinal) != 0 }.toSet()
        val temps = AcMode.entries.associateWith { m ->
            val lo = raw[1 + m.ordinal * 4]
            val hi = raw[2 + m.ordinal * 4]
            if (lo < 0 || hi < 0) null else (AcState.MIN_C + lo)..(AcState.MIN_C + hi)
        }
        val fans = AcMode.entries.associateWith { m ->
            val mask = raw[3 + m.ordinal * 4]
            FanSpeed.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()
        }
        // IRext's swing mask: bit 0 = swing on, bit 1 = swing off.
        val swing = AcMode.entries.associateWith { m -> raw[4 + m.ordinal * 4] and 1 != 0 }
        return AcCaps(modes.ifEmpty { AcMode.entries.toSet() }, temps, fans, swing)
    }

    companion object {
        /** IRext doesn't store a carrier; AC remotes use 38 kHz. */
        const val CARRIER_HZ = 38000
    }
}
