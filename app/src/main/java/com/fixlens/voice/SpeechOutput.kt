package com.fixlens.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.fixlens.app.TAG
import com.fixlens.voice.SpeechChunker.Piece
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Fixy's voice, fully on-device: Piper (VITS, sherpa-onnx) → AudioTrack.
 *
 * Pieces of text ([SpeechChunker]) are synthesized one at a time on `fixlens-tts` and played on `fixlens-tts-out`,
 * so the next piece is being made while the current one plays (Piper makes a clause in ~0.1-0.2 s). Silence the
 * model leaves at either end of a clip is trimmed and replaced with a short fixed pause. [stop] (a new turn,
 * barge-in) moves to a new epoch: queued pieces are dropped and the audio already handed to the track is flushed.
 * [dir] holds one Piper voice: its `.onnx`, `tokens.txt` and `espeak-ng-data/`. Callable from any thread.
 */
class SpeechOutput(
    private val dir: File,
    private val onSpeaking: (Boolean) -> Unit,
) {
    private sealed interface Task
    private class Say(val piece: Piece, val epoch: Int, val queuedAt: Long) : Task
    /** Re-renders the filler clips (after the voice changed). */
    private data object Render : Task
    private data object Quit : Task

    private class Clip(
        val samples: FloatArray,
        val pauseMs: Int,
        val epoch: Int,
        val label: String,
        val queuedAt: Long,
        val synthMs: Long,
    )

    private lateinit var tts: OfflineTts
    private lateinit var track: AudioTrack
    private var sampleRate = 44100
    @Volatile var ready = false
        private set
    @Volatile private var running = false

    private val epoch = AtomicInteger(0)
    /** When the current epoch began: a turn's start, so logged audio times read as "after the question". */
    @Volatile private var epochStart = System.currentTimeMillis()
    private val tasks = LinkedBlockingQueue<Task>()
    private val clips = LinkedBlockingQueue<Clip>()
    /** Pieces queued or being synthesized; the voice is only idle once this is 0 and the audio has played out. */
    private val pending = AtomicInteger(0)

    @Volatile private var speed = DEFAULT_SPEED
    @Volatile private var fillers: List<FloatArray> = emptyList()
    private val nextFiller = AtomicInteger(0)
    private var synthThread: Thread? = null
    private var playThread: Thread? = null

    /** Loads the model, warms it up and pre-renders the fillers. False (text-only Fixy) if the model is missing. */
    fun load(): Boolean {
        val model = dir.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull()
        if (model == null || !File(dir, "tokens.txt").exists() || !File(dir, "espeak-ng-data").isDirectory) {
            Log.w(TAG, "Voice model missing in $dir (need <voice>.onnx, tokens.txt, espeak-ng-data/). Fixy will answer in text only.")
            return false
        }
        val start = System.currentTimeMillis()
        try {
            tts = OfflineTts(
                assetManager = null,
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            tokens = path("tokens.txt"),
                            dataDir = path("espeak-ng-data"),
                        ),
                        numThreads = THREADS,
                        debug = false,
                        provider = "cpu",
                    ),
                    maxNumSentences = 1,
                ),
            )
            sampleRate = tts.sampleRate()
            val loadMs = System.currentTimeMillis() - start
            val warm = System.currentTimeMillis()
            synth("Hi there.") // the first run is slow (allocations, kernel selection)
            val warmMs = System.currentTimeMillis() - warm
            renderFillers()
            track = buildTrack()
            Log.i(
                TAG,
                "Voice load ok: model $loadMs ms, warm-up $warmMs ms, fillers ${fillers.map { "%.2f s".format(seconds(it)) }}, " +
                    "${model.name}, $sampleRate Hz, speed $speed, $THREADS threads",
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Voice failed to load", e)
            return false
        }
        running = true
        synthThread = Thread({ synthLoop() }, "fixlens-tts").apply { start() }
        playThread = Thread({ playLoop() }, "fixlens-tts-out").apply { start() }
        ready = true
        return true
    }

    /** The current epoch; pass it to [say]/[filler] so words from a turn that has since been stopped are dropped. */
    fun epoch(): Int = epoch.get()

    fun say(piece: Piece, epoch: Int) {
        if (!ready || epoch != this.epoch.get()) return
        pending.incrementAndGet()
        tasks.put(Say(piece, epoch, System.currentTimeMillis()))
    }

    /** A whole text (a KB line, the greeting), spoken verbatim, cut into pieces so its first words start early. */
    fun speak(text: String, epoch: Int) = SpeechChunker.split(text).forEach { say(it, epoch) }

    /** A short pre-rendered "Let me look." that plays at once, to cover the wait for the VLM. */
    fun filler(epoch: Int) {
        val all = fillers
        if (!ready || all.isEmpty() || epoch != this.epoch.get()) return
        clips.put(Clip(all[nextFiller.getAndIncrement() % all.size], SENTENCE_PAUSE_MS, epoch, "filler", System.currentTimeMillis(), 0))
    }

    /** Stops speaking now: everything queued is dropped and what's already in the track is flushed. */
    fun stop() {
        epoch.incrementAndGet()
        epochStart = System.currentTimeMillis()
        val dropped = mutableListOf<Task>()
        tasks.drainTo(dropped)
        pending.addAndGet(-dropped.count { it is Say })
        dropped.filter { it !is Say }.forEach(tasks::put)
        clips.clear()
    }

    /** Debug: speaking speed (1 = the voice's natural pace). */
    fun configure(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 2f)
        Log.i(TAG, "Voice speed ${this.speed}")
        if (ready) tasks.put(Render)
    }

    fun release() {
        if (!running) return
        running = false
        stop()
        tasks.put(Quit)
        synthThread?.join(1000)
        playThread?.join(500)
        track.release()
        tts.release()
        ready = false
    }

    // ---- fixlens-tts ----

    private fun synthLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        while (true) {
            when (val task = tasks.take()) {
                Quit -> return
                Render -> runCatching { renderFillers() }.onFailure { Log.e(TAG, "Filler render failed", it) }
                is Say -> {
                    if (task.epoch == epoch.get()) {
                        val start = System.currentTimeMillis()
                        val samples = runCatching { trim(synth(task.piece.text)) }
                            .onFailure { Log.e(TAG, "Voice failed on \"${task.piece.text}\"", it) }.getOrNull()
                        val synthMs = System.currentTimeMillis() - start
                        if (samples != null && samples.isNotEmpty() && task.epoch == epoch.get()) {
                            val pause = if (task.piece.sentenceEnd) SENTENCE_PAUSE_MS else CLAUSE_PAUSE_MS
                            clips.put(Clip(samples, pause, task.epoch, task.piece.text, task.queuedAt, synthMs))
                        }
                    }
                    pending.decrementAndGet()
                }
            }
        }
    }

    private fun synth(text: String): FloatArray = tts.generate(text, sid = 0, speed = speed).samples

    private fun renderFillers() {
        fillers = FILLERS.map { trim(synth(it)) }
    }

    // ---- fixlens-tts-out ----

    private fun playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var current = epoch.get()
        var speaking = false
        /** Frames written since the last flush, and the head position right after it. */
        var written = 0L
        var base = track.playbackHeadPosition.toLong()
        val silence = FloatArray(sampleRate * SENTENCE_PAUSE_MS / 1000)

        fun setSpeaking(on: Boolean) {
            if (speaking == on) return
            speaking = on
            onSpeaking(on)
        }

        while (running) {
            if (epoch.get() != current) {
                current = epoch.get()
                track.pause()
                track.flush()
                track.play()
                written = 0
                base = track.playbackHeadPosition.toLong()
                setSpeaking(false)
            }
            val clip = clips.poll(POLL_MS, TimeUnit.MILLISECONDS)
            if (clip == null) {
                val played = track.playbackHeadPosition.toLong() - base
                if (speaking && pending.get() == 0 && clips.isEmpty() && played >= written) setSpeaking(false)
                continue
            }
            if (clip.epoch != epoch.get()) continue
            setSpeaking(true)
            val now = System.currentTimeMillis()
            val secs = seconds(clip.samples)
            Log.i(
                TAG,
                "Voice \"${clip.label.take(40)}\": audio at +${now - epochStart} ms, ${now - clip.queuedAt} ms after queued " +
                    "(synth ${clip.synthMs} ms for %.2f s, RTF %.2f)".format(secs, clip.synthMs / 1000.0 / secs),
            )
            written += write(clip.samples, clip.samples.size, clip.epoch)
            written += write(silence, sampleRate * clip.pauseMs / 1000, clip.epoch)
        }
    }

    /** Writes [count] frames in small slices, so a [stop] cuts in within one slice. Returns the frames written. */
    private fun write(samples: FloatArray, count: Int, clipEpoch: Int): Long {
        var off = 0
        while (off < count && clipEpoch == epoch.get() && running) {
            val n = track.write(samples, off, minOf(SLICE, count - off), AudioTrack.WRITE_BLOCKING)
            if (n <= 0) break
            off += n
        }
        return off.toLong()
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Media, not ASSISTANT/voice-call routing: normal volume and no call processing while the mic is open.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minBuf * 2, sampleRate * 4 * BUFFER_MS / 1000))
            .build()
            .apply { play() }
    }

    /** Cuts the model's silent padding, keeping a few ms before the first sound and a short tail after the last. */
    private fun trim(s: FloatArray): FloatArray {
        val first = s.indexOfFirst { abs(it) > SILENCE }
        if (first < 0) return FloatArray(0)
        val last = s.indexOfLast { abs(it) > SILENCE }
        val from = maxOf(0, first - sampleRate * LEAD_MS / 1000)
        val to = minOf(s.size, last + 1 + sampleRate * TAIL_MS / 1000)
        return s.copyOfRange(from, to)
    }

    private fun seconds(s: FloatArray) = s.size.toDouble() / sampleRate
    private fun path(name: String) = File(dir, name).absolutePath

    companion object {
        const val DEFAULT_SPEED = 1.0f
        /** The VLM decodes on 4; STT is idle while Fixy speaks. */
        const val THREADS = 2

        val FILLERS = listOf("Let me look.", "Okay, let me see.", "One moment.")

        private const val SILENCE = 0.01f
        private const val LEAD_MS = 15
        private const val TAIL_MS = 60
        private const val CLAUSE_PAUSE_MS = 120
        private const val SENTENCE_PAUSE_MS = 250
        private const val BUFFER_MS = 100
        private const val SLICE = 1024
        private const val POLL_MS = 20L
    }
}
