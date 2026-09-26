package com.fixlens.vision

import android.util.Log
import com.fixlens.app.TAG
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * On-device VLM (Qwen3-VL via MNN). One request at a time on a dedicated thread, never per frame.
 *
 * The KV cache is kept between calls (`reuse_kv`), and prompts are raw chat-template text built by the
 * caller (`use_template` off), so each call appends to the conversation. Images go inside the prompt as
 * `<img>/abs/path.jpg</img>`.
 */
class VlmEngine {

    fun interface TextCallback {
        fun onText(text: String)
    }

    /** What happens to a turn's tokens in the KV cache once it finishes. */
    enum class Keep(val code: Int) {
        /** Side request (e.g. naming a session): always rolled back. */
        Never(0),
        /** Conversation turn: kept, unless it was cancelled mid-way. */
        UnlessCancelled(1),
    }

    data class Result(val cancelled: Boolean, val kvTokens: Int, val stats: String)

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "fixlens-vlm") }
        .asCoroutineDispatcher()
    private var handle = 0L

    val isLoaded: Boolean get() = handle != 0L

    suspend fun load(modelDir: File, tmpDir: File): Boolean = withContext(dispatcher) {
        val config = File(modelDir, "config.json")
        if (!config.exists()) {
            Log.e(TAG, "VLM config missing: $config")
            return@withContext false
        }
        tmpDir.mkdirs()
        val extra = org.json.JSONObject()
            .put("backend_type", "cpu")
            .put("thread_num", 4)
            .put("precision", "low")
            .put("sampler_type", "greedy")
            .put("max_new_tokens", MAX_NEW_TOKENS)
            .put("reuse_kv", true)
            .put("use_template", false)
            .put("max_all_tokens", MAX_CONTEXT_TOKENS)
            .put("tmp_path", tmpDir.absolutePath)
            .toString()
        val start = System.currentTimeMillis()
        handle = nativeLoad(config.absolutePath, extra)
        Log.i(TAG, "VLM load ${if (handle != 0L) "ok" else "FAILED"} in ${System.currentTimeMillis() - start} ms")
        handle != 0L
    }

    /**
     * Appends [prompt] (raw chat-template text) to the conversation and streams the reply to [onText].
     * With [resetFirst] the KV cache is cleared first. Calls queue on the VLM thread, so callers should
     * [cancel] a running one before asking again.
     */
    suspend fun generate(
        prompt: String,
        keep: Keep,
        resetFirst: Boolean = false,
        maxTokens: Int = MAX_NEW_TOKENS,
        onText: (String) -> Unit,
    ): Result = withContext(dispatcher) {
        check(handle != 0L) { "VLM not loaded" }
        val raw = nativeGenerate(handle, prompt, maxTokens, keep.code, resetFirst, TextCallback(onText))
        val (cancelled, kv, stats) = raw.split(" ", limit = 3)
        Result(cancelled == "1", kv.toInt(), stats)
    }

    /** Clears the conversation from the KV cache. */
    suspend fun reset() = withContext(dispatcher) { if (handle != 0L) nativeReset(handle) }

    /** Stops the running generation at the next token (prefill itself can't be interrupted). Thread-safe. */
    fun cancel() {
        if (handle != 0L) nativeCancel(handle)
    }

    private external fun nativeLoad(configPath: String, extraConfig: String): Long
    private external fun nativeGenerate(
        handle: Long, prompt: String, maxTokens: Int, keep: Int, resetFirst: Boolean, callback: TextCallback,
    ): String
    private external fun nativeReset(handle: Long)
    private external fun nativeCancel(handle: Long)
    private external fun nativeRelease(handle: Long)

    companion object {
        /** Pointing JSON (~25 tokens per box, ~17 per point: 13 screws ≈ 220) + two short sentences. */
        const val MAX_NEW_TOKENS = 320
        /** The whole conversation budget (MNN defaults to 2048). ~147 KB of KV per token for the 4B model. */
        const val MAX_CONTEXT_TOKENS = 4096

        init {
            System.loadLibrary("MNN")
            System.loadLibrary("fixlensllm")
        }
    }
}
