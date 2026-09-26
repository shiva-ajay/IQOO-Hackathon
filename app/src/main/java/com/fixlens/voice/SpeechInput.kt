package com.fixlens.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.fixlens.app.TAG
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fully on-device push-to-talk: AudioRecord (16 kHz mono) → Moonshine (sherpa-onnx), with Silero VAD as a gate.
 * The mic stream stays open while a session is on screen, but audio is only kept while the talk button is held
 * ([press] … [release]), plus a short pre-roll and tail so the first and last words aren't clipped. While held,
 * the take so far is re-decoded every ~0.5 s for live captions. On release, the VAD finds the speech in the take:
 * a take with no speech is dropped ([onNothingHeard]); otherwise just the speech span is decoded and delivered.
 * Decoding runs on its own thread, so capture never drops samples.
 */
class SpeechInput(
    private val sttDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onNothingHeard: () -> Unit,
    private val onLevel: (Float) -> Unit,
) {
    private lateinit var vad: Vad
    private lateinit var recognizer: OfflineRecognizer
    @Volatile private var running = false
    @Volatile private var holding = false
    private var thread: Thread? = null
    /** Incremented per press (and on stop); results from an older take are dropped. */
    private val take = AtomicInteger(0)
    /** VAD and recognizer are only touched here, one job at a time. */
    private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r, "fixlens-stt") }
    private val partialQueued = AtomicBoolean(false)

    fun load(): Boolean {
        val moonshine = File(sttDir, "moonshine-base-en")
        val vadModel = File(sttDir, "silero_vad.onnx")
        if (!vadModel.exists() || !File(moonshine, "tokens.txt").exists()) {
            Log.e(TAG, "STT models missing in $sttDir")
            return false
        }
        val start = System.currentTimeMillis()
        vad = Vad(
            assetManager = null,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadModel.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.7f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW,
                    maxSpeechDuration = 30f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
            ),
        )
        recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        preprocessor = File(moonshine, "preprocess.onnx").absolutePath,
                        encoder = File(moonshine, "encode.int8.onnx").absolutePath,
                        uncachedDecoder = File(moonshine, "uncached_decode.int8.onnx").absolutePath,
                        cachedDecoder = File(moonshine, "cached_decode.int8.onnx").absolutePath,
                    ),
                    tokens = File(moonshine, "tokens.txt").absolutePath,
                    numThreads = 2,
                ),
            ),
        )
        Log.i(TAG, "STT load ok in ${System.currentTimeMillis() - start} ms")
        return true
    }

    /** Opens the mic stream. Nothing is kept or decoded until [press]. */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the camera screen is shown
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "fixlens-audio").apply { start() }
    }

    /** Closes the mic stream and drops any take in progress. */
    fun stop() {
        running = false
        holding = false
        take.incrementAndGet()
        thread?.join(500)
        thread = null
        onLevel(0f)
    }

    /** The talk button went down: start a new take. */
    fun press() {
        take.incrementAndGet()
        holding = true
    }

    /** The talk button came up: finish the take after a short tail. */
    fun release() {
        holding = false
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, WINDOW * 8),
        )
        val pcm = ShortArray(WINDOW)
        val preroll = FloatArray(PREROLL)
        var prerollPos = 0
        var prerollFull = false
        val buf = PcmBuffer(SAMPLE_RATE * 10)
        var capturing = false
        var capturedTake = 0
        var heldSamples = 0
        var tailLeft = 0
        var lastPartialAt = 0L
        record.startRecording()
        try {
            while (running) {
                val n = record.read(pcm, 0, pcm.size)
                if (n <= 0) continue
                val samples = FloatArray(n) { pcm[it] / 32768f }

                if (holding && (!capturing || take.get() != capturedTake)) {
                    // A new take: start from the pre-roll, so a word begun as the finger lands isn't cut.
                    // (A re-press during the previous take's tail just starts over; that pre-roll is stale.)
                    buf.clear()
                    if (!capturing) {
                        if (prerollFull) buf.add(preroll, prerollPos, PREROLL - prerollPos)
                        buf.add(preroll, 0, prerollPos)
                    }
                    capturing = true
                    capturedTake = take.get()
                    heldSamples = 0
                    tailLeft = TAIL
                    lastPartialAt = System.currentTimeMillis()
                }
                if (!capturing) {
                    for (v in samples) {
                        preroll[prerollPos] = v
                        prerollPos = (prerollPos + 1) % PREROLL
                        if (prerollPos == 0) prerollFull = true
                    }
                    continue
                }

                buf.add(samples, 0, n)
                if (holding) {
                    heldSamples += n
                    onLevel(loudness(samples))
                    val now = System.currentTimeMillis()
                    if (now - lastPartialAt > PARTIAL_INTERVAL_MS && heldSamples > SAMPLE_RATE / 3 && !partialQueued.get()) {
                        lastPartialAt = now
                        partial(buf.copy(), capturedTake)
                    }
                    if (buf.size < MAX_TAKE) continue
                    Log.i(TAG, "STT take hit ${MAX_TAKE / SAMPLE_RATE} s, finishing it")
                    holding = false
                } else {
                    tailLeft -= n
                    if (tailLeft > 0) continue
                }
                onLevel(0f)
                finish(buf.copy(), heldSamples, capturedTake)
                capturing = false
                prerollPos = 0
                prerollFull = false
            }
        } finally {
            record.stop()
            record.release()
        }
    }

    private fun partial(samples: FloatArray, id: Int) {
        partialQueued.set(true)
        decoder.execute {
            partialQueued.set(false)
            if (take.get() != id) return@execute
            val text = decode(samples)
            if (text.isNotBlank() && take.get() == id && holding) onPartial(text)
        }
    }

    /** Gates the take on the VAD, then decodes just the speech span. */
    private fun finish(samples: FloatArray, heldSamples: Int, id: Int) {
        decoder.execute {
            if (take.get() != id) return@execute
            val start = System.currentTimeMillis()
            if (heldSamples < MIN_HOLD) {
                Log.i(TAG, "STT take too short (${heldSamples * 1000 / SAMPLE_RATE} ms held), dropped")
                onNothingHeard()
                return@execute
            }
            val span = speechSpan(samples)
            if (span == null) {
                Log.i(TAG, "STT take ${samples.size * 1000 / SAMPLE_RATE} ms: no speech, dropped")
                onNothingHeard()
                return@execute
            }
            val text = decode(samples.copyOfRange(span.first, span.last + 1))
            Log.i(
                TAG,
                "STT final in ${System.currentTimeMillis() - start} ms " +
                    "(take ${samples.size * 1000 / SAMPLE_RATE} ms, speech ${span.count() * 1000 / SAMPLE_RATE} ms): \"$text\"",
            )
            if (take.get() != id) return@execute
            if (text.isBlank()) onNothingHeard() else onFinal(text)
        }
    }

    /** First speech start to last speech end in [samples], padded; null when the VAD hears no speech. */
    private fun speechSpan(samples: FloatArray): IntRange? {
        vad.reset()
        var i = 0
        while (i + WINDOW <= samples.size) {
            vad.acceptWaveform(samples.copyOfRange(i, i + WINDOW))
            i += WINDOW
        }
        vad.flush()
        var first = Int.MAX_VALUE
        var last = -1
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            first = minOf(first, segment.start)
            last = maxOf(last, segment.start + segment.samples.size)
        }
        if (last < 0) return null
        if (last > samples.size + WINDOW) {
            // Offsets not relative to this take: fall back to decoding all of it rather than a wrong slice.
            Log.w(TAG, "VAD segment end $last beyond take of ${samples.size} samples, decoding the whole take")
            return samples.indices
        }
        val from = (first - SPAN_PAD).coerceIn(0, samples.size - 1)
        val to = (last + SPAN_PAD).coerceIn(from + 1, samples.size)
        return from until to
    }

    private fun decode(samples: FloatArray): String {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, SAMPLE_RATE)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text.trim()
        stream.release()
        return collapseRepeats(text)
    }

    /** RMS loudness mapped from -50..-10 dBFS to 0..1, for the listening animation. */
    private fun loudness(samples: FloatArray): Float {
        var sum = 0.0
        for (v in samples) sum += v * v
        val rms = kotlin.math.sqrt(sum / samples.size).coerceAtLeast(1e-6)
        val db = 20 * kotlin.math.log10(rms)
        return ((db + 50) / 40).toFloat().coerceIn(0f, 1f)
    }

    /** A growable float buffer, so a take isn't boxed sample by sample. */
    private class PcmBuffer(capacity: Int) {
        private var data = FloatArray(capacity)
        var size = 0
            private set

        fun add(src: FloatArray, from: Int, count: Int) {
            if (count <= 0) return
            if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
            System.arraycopy(src, from, data, size, count)
            size += count
        }

        fun copy(): FloatArray = data.copyOf(size)

        fun clear() {
            size = 0
        }
    }

    companion object {
        /**
         * Moonshine sometimes loops on noisy audio ("It's not like that. It's not like that.").
         * Drops a sentence that repeats the previous one.
         */
        fun collapseRepeats(text: String): String {
            val sentences = Regex("""[^.!?]+[.!?]*""").findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }
            val out = mutableListOf<String>()
            for (sentence in sentences) {
                val key = sentence.lowercase().trimEnd('.', '!', '?')
                if (out.isEmpty() || out.last().lowercase().trimEnd('.', '!', '?') != key) out += sentence
            }
            return out.joinToString(" ")
        }

        private const val SAMPLE_RATE = 16000
        private const val WINDOW = 512
        private const val PARTIAL_INTERVAL_MS = 500L
        /** Kept from just before the press. */
        private const val PREROLL = SAMPLE_RATE * 3 / 10
        /** Kept after the release: people let go as the last word ends. */
        private const val TAIL = SAMPLE_RATE * 3 / 10
        /** A shorter hold is a tap, not a question. */
        private const val MIN_HOLD = SAMPLE_RATE / 4
        /** Speech span padding, so the VAD doesn't clip word edges. */
        private const val SPAN_PAD = SAMPLE_RATE / 5
        private const val MAX_TAKE = SAMPLE_RATE * 30
    }
}
