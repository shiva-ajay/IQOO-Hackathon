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

/**
 * Fully on-device speech input: AudioRecord (16 kHz mono) → Silero VAD → Moonshine (sherpa-onnx).
 * While the user speaks, the audio so far is re-decoded every ~0.5 s for live captions; when the
 * VAD detects a pause, the whole segment is decoded once more and delivered as the final transcript.
 */
class SpeechInput(
    private val sttDir: File,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
) {
    private lateinit var vad: Vad
    private lateinit var recognizer: OfflineRecognizer
    @Volatile private var running = false
    private var thread: Thread? = null

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
                    minSilenceDuration = 0.7f, // the "pause" that ends a question
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW,
                    maxSpeechDuration = 15f,
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

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the camera screen is shown
    fun start() {
        if (running) return
        vad.reset()
        running = true
        thread = Thread({ loop() }, "fixlens-audio").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        onLevel(0f)
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, WINDOW * 4),
        )
        val pcm = ShortArray(WINDOW)
        val live = ArrayList<Float>(SAMPLE_RATE * 15)
        var lastPartialAt = 0L
        record.startRecording()
        try {
            while (running) {
                val n = record.read(pcm, 0, pcm.size)
                if (n <= 0) continue
                val samples = FloatArray(n) { pcm[it] / 32768f }
                onLevel(loudness(samples))
                vad.acceptWaveform(samples)

                if (vad.isSpeechDetected()) {
                    samples.forEach { live.add(it) }
                    val now = System.currentTimeMillis()
                    if (now - lastPartialAt > PARTIAL_INTERVAL_MS && live.size > SAMPLE_RATE / 3) {
                        lastPartialAt = now
                        val text = decode(live.toFloatArray())
                        if (text.isNotBlank()) onPartial(text)
                    }
                }
                while (!vad.empty()) {
                    val segment = vad.front()
                    vad.pop()
                    live.clear()
                    val start = System.currentTimeMillis()
                    val text = decode(segment.samples)
                    Log.i(TAG, "STT final in ${System.currentTimeMillis() - start} ms: \"$text\"")
                    if (text.isNotBlank()) onFinal(text)
                }
            }
        } finally {
            record.stop()
            record.release()
        }
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
    }
}
