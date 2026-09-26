package com.fixlens.guide

import android.util.Log
import com.fixlens.app.TAG
import com.fixlens.session.RepairSession
import com.fixlens.vision.VlmEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decides what goes into the model for each turn (see docs/sessions-plan.md §3).
 *
 * The KV cache holds exactly one session's conversation. Within that session a turn only appends the new
 * question + picture, so earlier turns cost nothing. Opening another session, or nearing the context
 * limit, triggers a rebuild: system prompt + session notes + the last few turns as text + the new turn.
 *
 * Every call holds [lock], so a turn asked while a prewarm or title request is running waits for it and
 * then sees the cache state it left.
 */
class ConversationContext(private val vlm: VlmEngine) {

    /** Session whose conversation is in the KV cache, or null if the cache is empty or unknown. */
    private var liveSessionId: String? = null
    private var kvTokens = 0
    /** True when the cache ends inside an answer that still needs its end token (see [FixyPrompts.CLOSE_ANSWER]). */
    private var answerOpen = false
    private val lock = Mutex()

    /**
     * Rebuilds the cache for [session] ahead of the first question (prefill only, nothing generated), so
     * opening a session costs its replay now, while the user is still framing the shot.
     */
    suspend fun prewarm(session: RepairSession) = lock.withLock {
        if (liveSessionId == session.id) return@withLock
        val replay = session.turns.takeLast(REPLAY_TURNS)
        val prompt = buildString {
            append(FixyPrompts.system(MemoryRules.render(session.memory)))
            replay.forEachIndexed { i, t ->
                if (i < replay.lastIndex) append(FixyPrompts.pastTurn(t.question, t.answer))
                else append(FixyPrompts.userTurn(t.question)).append(t.answer) // left open, like after a live turn
            }
        }
        val result = runGuarded { vlm.generate(prompt, VlmEngine.Keep.UnlessCancelled, resetFirst = true, maxTokens = 0) {} }
        kvTokens = result.kvTokens
        liveSessionId = session.id
        answerOpen = replay.isNotEmpty()
        Log.i(TAG, "Prewarmed ${session.id} with ${replay.size} turns (${result.stats})")
    }

    /**
     * Appends a question turn (picture + [question] + optional [instruction], e.g. the grounding request) and
     * streams the reply. Only [question] is replayed on a later rebuild; the instruction isn't.
     */
    suspend fun ask(
        session: RepairSession,
        imagePath: String?,
        question: String,
        instruction: String? = null,
        onText: (String) -> Unit,
    ): VlmEngine.Result = lock.withLock {
        val rebuild = liveSessionId != session.id || kvTokens > COMPACT_AT_TOKENS
        val content = (imagePath?.let(FixyPrompts::image) ?: "") + question + (instruction?.let { "\n\n$it" } ?: "")
        val prompt = if (rebuild) {
            buildString {
                append(FixyPrompts.system(MemoryRules.render(session.memory)))
                session.turns.takeLast(REPLAY_TURNS).forEach { append(FixyPrompts.pastTurn(it.question, it.answer)) }
                append(FixyPrompts.userTurn(content))
            }
        } else {
            (if (answerOpen) FixyPrompts.CLOSE_ANSWER else "") + FixyPrompts.userTurn(content)
        }
        if (rebuild) Log.i(TAG, "Context rebuild for ${session.id}: ${session.turns.size} turns saved, replaying ${minOf(REPLAY_TURNS, session.turns.size)} (kv was $kvTokens)")

        val result = runGuarded { vlm.generate(prompt, VlmEngine.Keep.UnlessCancelled, resetFirst = rebuild, onText = onText) }
        kvTokens = result.kvTokens
        if (rebuild && result.cancelled) {
            liveSessionId = null // a cancelled rebuild leaves the cache empty; the next turn rebuilds again
        } else {
            liveSessionId = session.id
            if (!result.cancelled) answerOpen = true
        }
        result
    }

    /**
     * Asks for a short session title as a side request: it sees the conversation already in the cache and
     * is rolled back afterwards, so it never becomes part of the conversation. Only valid for the live session.
     */
    suspend fun suggestTitle(session: RepairSession): String? = lock.withLock {
        if (liveSessionId != session.id) return@withLock null
        val out = StringBuilder()
        val result = runGuarded {
            vlm.generate(
                (if (answerOpen) FixyPrompts.CLOSE_ANSWER else "") + FixyPrompts.userTurn(FixyPrompts.TITLE_REQUEST),
                VlmEngine.Keep.Never,
                maxTokens = TITLE_MAX_TOKENS,
            ) { out.append(it) }
        }
        kvTokens = result.kvTokens
        Log.i(TAG, "Title suggestion \"${out.toString().trim()}\" (${result.stats})")
        if (result.cancelled) null else FixyPrompts.cleanTitle(out.toString())
    }

    /**
     * Re-ground side request: where is [label] in the picture at [imagePath] now? It sees the live conversation
     * and is rolled back afterwards. Returns null without waiting when the VLM is busy (a question always
     * wins) or [session] isn't the one in the cache.
     */
    suspend fun locate(session: RepairSession, imagePath: String, label: String, onText: (String) -> Unit): VlmEngine.Result? {
        if (!lock.tryLock()) return null
        try {
            if (liveSessionId != session.id) return null
            val result = runGuarded {
                vlm.generate(
                    (if (answerOpen) FixyPrompts.CLOSE_ANSWER else "") +
                        FixyPrompts.userTurn(FixyPrompts.image(imagePath) + FixyPrompts.locate(label)),
                    VlmEngine.Keep.Never,
                    maxTokens = LOCATE_MAX_TOKENS,
                    onText = onText,
                )
            }
            kvTokens = result.kvTokens
            return result
        } finally {
            lock.unlock()
        }
    }

    /** Forget the cache, e.g. when the session it holds is deleted. */
    suspend fun invalidate(sessionId: String? = null) = lock.withLock {
        if (sessionId == null || sessionId == liveSessionId) {
            vlm.reset()
            liveSessionId = null
            kvTokens = 0
            answerOpen = false
        }
    }

    /** If the engine throws, the cache state is unknown: make the next turn rebuild. */
    private inline fun <T> runGuarded(block: () -> T): T =
        try { block() } catch (e: Throwable) { liveSessionId = null; throw e }

    private companion object {
        /** Rebuild (dropping old pictures) once the conversation passes this, leaving room for a turn. */
        const val COMPACT_AT_TOKENS = VlmEngine.MAX_CONTEXT_TOKENS - 800
        /** Past turns replayed as text on a rebuild; the session notes carry the rest. */
        const val REPLAY_TURNS = 2
        const val TITLE_MAX_TOKENS = 12
        /** Enough for one box line; the caller stops earlier once the box is parsed. */
        const val LOCATE_MAX_TOKENS = 48
    }
}
