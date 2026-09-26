package com.fixlens.guide

import android.util.Log
import com.fixlens.app.TAG
import com.fixlens.session.RepairSession
import com.fixlens.session.Turn
import com.fixlens.vision.VlmEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    /** The `<past_repairs>` block the cache was built with; a change (it's dropped after the first turn) rebuilds. */
    private var livePastRepairs: String? = null
    private var kvTokens = 0
    /** True when the cache ends inside an answer that still needs its end token (see [FixyPrompts.CLOSE_ANSWER]). */
    private var answerOpen = false
    private val lock = Mutex()

    /**
     * Rebuilds the cache for [session] ahead of the first question (prefill only, nothing generated), so
     * opening a session costs its replay now, while the user is still framing the shot. Also used right after
     * the reply to a follow-up greeting, to drop the earlier repair from the cache before the next question.
     */
    suspend fun prewarm(session: RepairSession, pastRepairs: String?) = lock.withLock {
        if (!needsRebuild(session, pastRepairs)) return@withLock
        val replay = replayed(session)
        val prompt = buildString {
            append(header(session, pastRepairs))
            replay.forEachIndexed { i, t ->
                if (i < replay.lastIndex) append(FixyPrompts.pastTurn(t.question, t.answer))
                else append(FixyPrompts.userTurn(t.question)).append(t.answer) // left open, like after a live turn
            }
        }
        val result = runGuarded { vlm.generate(prompt, VlmEngine.Keep.UnlessCancelled, resetFirst = true, maxTokens = 0) {} }
        kvTokens = result.kvTokens
        liveSessionId = session.id
        livePastRepairs = pastRepairs
        answerOpen = replay.isNotEmpty()
        Log.i(TAG, "Prewarmed ${session.id} with ${replay.size} turns (${result.stats})")
    }

    /**
     * Appends a question turn (picture + [question] + optional [instruction], e.g. the grounding request) and
     * streams the reply. Only [question] is replayed on a later rebuild; the instruction isn't. [pastRepairs] is
     * only used when the cache is rebuilt.
     */
    suspend fun ask(
        session: RepairSession,
        pastRepairs: String?,
        imagePath: String?,
        question: String,
        instruction: String? = null,
        onText: (String) -> Unit,
    ): VlmEngine.Result = lock.withLock {
        val rebuild = needsRebuild(session, pastRepairs)
        val content = (imagePath?.let(FixyPrompts::image) ?: "") + question + (instruction?.let { "\n\n$it" } ?: "")
        val prompt = if (rebuild) {
            buildString {
                append(header(session, pastRepairs))
                replayed(session).forEach { append(FixyPrompts.pastTurn(it.question, it.answer)) }
                append(FixyPrompts.userTurn(content))
            }
        } else {
            (if (answerOpen) FixyPrompts.CLOSE_ANSWER else "") + FixyPrompts.userTurn(content)
        }
        if (rebuild) Log.i(TAG, "Context rebuild for ${session.id}: ${session.turns.size} turns saved, replaying ${replayed(session).size} (kv was $kvTokens)")

        val result = runGuarded { vlm.generate(prompt, VlmEngine.Keep.UnlessCancelled, resetFirst = rebuild, onText = onText) }
        kvTokens = result.kvTokens
        if (rebuild && result.cancelled) {
            liveSessionId = null // a cancelled rebuild leaves the cache empty; the next turn rebuilds again
        } else {
            liveSessionId = session.id
            if (rebuild) livePastRepairs = pastRepairs
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

    /** Re-ground side request: where are the parts named [labels] now? See [side]. */
    suspend fun locate(session: RepairSession, imagePath: String, labels: List<String>, onText: (String) -> Unit): VlmEngine.Result? =
        side(session, imagePath, FixyPrompts.locate(labels), LOCATE_MAX_TOKENS, wait = false, onText)

    /**
     * A side request about the picture at [imagePath] ([request]: point at a KB step's target, check a step,
     * re-ground). It sees the live conversation and is rolled back afterwards. With [wait] it queues behind a
     * running question (the user asked for it); otherwise it returns null at once when the VLM is busy. Also
     * null when [session] isn't the one in the cache.
     */
    suspend fun side(
        session: RepairSession,
        imagePath: String,
        request: String,
        maxTokens: Int,
        wait: Boolean,
        onText: (String) -> Unit,
    ): VlmEngine.Result? {
        if (wait) lock.lock() else if (!lock.tryLock()) return null
        try {
            if (liveSessionId != session.id) return null
            // The native call runs to the end even if our caller is cancelled (a new step, a new question), and it
            // rolls itself back; wait for it so the cache bookkeeping stays right, instead of treating the
            // cancellation as an engine failure (which would force a full rebuild).
            val result = withContext(NonCancellable) {
                runGuarded {
                    vlm.generate(
                        (if (answerOpen) FixyPrompts.CLOSE_ANSWER else "") +
                            FixyPrompts.userTurn(FixyPrompts.image(imagePath) + request),
                        VlmEngine.Keep.Never,
                        maxTokens = maxTokens,
                        onText = onText,
                    )
                }
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
            livePastRepairs = null
            kvTokens = 0
            answerOpen = false
        }
    }

    private fun needsRebuild(session: RepairSession, pastRepairs: String?) =
        liveSessionId != session.id || pastRepairs != livePastRepairs || kvTokens > COMPACT_AT_TOKENS

    /**
     * The greeting only before the first question. Once the user has answered it, it and a reply about the earlier
     * repair it asked after ("yes, the car's fine now") are left out, so the old device doesn't color this session.
     */
    private fun header(session: RepairSession, pastRepairs: String?) = FixyPrompts.system(
        MemoryRules.render(session.memory),
        pastRepairs,
        session.greeting?.takeIf { session.turns.isEmpty() },
    )

    private fun replayed(session: RepairSession): List<Turn> =
        session.turns.drop(if (MemoryRules.openedWithFollowUp(session)) 1 else 0).takeLast(REPLAY_TURNS)

    /** If the engine throws, the cache state is unknown: make the next turn rebuild. */
    private inline fun <T> runGuarded(block: () -> T): T =
        try { block() } catch (e: Throwable) { liveSessionId = null; throw e }

    private companion object {
        /** Rebuild (dropping old pictures) once the conversation passes this, leaving room for a turn. */
        const val COMPACT_AT_TOKENS = VlmEngine.MAX_CONTEXT_TOKENS - 800
        /** Past turns replayed as text on a rebuild; the session notes carry the rest. */
        const val REPLAY_TURNS = 2
        const val TITLE_MAX_TOKENS = 12
        /** Enough for a list of ~16 points; the caller stops as soon as the JSON is complete. */
        const val LOCATE_MAX_TOKENS = 300
    }
}
