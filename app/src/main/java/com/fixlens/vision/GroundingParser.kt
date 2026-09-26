package com.fixlens.vision

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * Splits the VLM's streamed reply into the box line (first) and the spoken text (after it), as chunks arrive.
 *
 * The reply should start with a JSON box: `{"bbox_2d":[x1,y1,x2,y2],"label":"…"}`, `{"bbox_2d":null}`, or
 * Qwen3-VL's native `[{"bbox_2d":…}]`, optionally inside a ```json fence and possibly over several lines.
 * [onGrounding] fires once, as soon as that JSON is complete, before the text finishes. Everything after it
 * goes to [onText]. If the reply doesn't start with JSON, all of it is text (CLAUDE.md §9). JSON that is
 * never completed or can't be read is dropped, so a box line never reaches the captions.
 *
 * Not thread-safe: feed it from one thread (the VLM callback).
 */
class GroundingParser(
    private val onGrounding: (Grounding) -> Unit,
    private val onText: (String) -> Unit,
) {
    sealed interface Grounding {
        data class Box(val box: ModelBox) : Grounding
        /** The model answered `null` / `[]`: nothing to point at. */
        data object NoBox : Grounding
        /** The reply didn't start with JSON: the format wasn't followed. */
        data object NotJson : Grounding
        /** It started like JSON but was cut off or unreadable. */
        data object Invalid : Grounding
    }

    private val head = StringBuilder()
    private val textOut = StringBuilder()
    /** The answer's first characters, held back until we know whether they're a "Fixy:" speaker tag. */
    private val lead = StringBuilder()
    private var resolved = false

    /** The spoken text so far (never includes the box line). */
    val text: String get() = textOut.toString()

    var grounding: Grounding? = null
        private set

    fun feed(chunk: String) {
        if (resolved) {
            emitText(chunk)
            return
        }
        head.append(chunk)
        resolve(final = false)
    }

    /** Call when the stream ends, so a reply that never completed its first line is still resolved. */
    fun finish() {
        if (!resolved) resolve(final = true)
        if (lead.isNotEmpty()) flushLead()
    }

    private fun resolve(final: Boolean) {
        val s = head.toString()
        val start = s.indexOfFirst { !it.isWhitespace() }
        if (start < 0) {
            if (final) settle(Grounding.NotJson, rest = "")
            return
        }
        when {
            s.startsWith(FENCE, start) -> resolveFenced(s, start, final)
            s[start] == '{' || s[start] == '[' -> {
                val end = jsonEnd(s, start)
                when {
                    end >= 0 -> settle(parse(s.substring(start, end + 1)), rest = s.substring(end + 1))
                    final || s.length > MAX_HEAD -> settle(Grounding.Invalid, rest = "")
                }
            }
            // A fence may still be forming ("`" or "``" so far).
            s.length - start < FENCE.length && FENCE.startsWith(s.substring(start)) && !final -> Unit
            else -> settle(Grounding.NotJson, rest = s)
        }
    }

    private fun resolveFenced(s: String, start: Int, final: Boolean) {
        val bodyStart = s.indexOf('\n', start)
        val close = if (bodyStart >= 0) s.indexOf(FENCE, bodyStart) else -1
        if (close < 0) {
            if (final || s.length > MAX_HEAD) settle(Grounding.Invalid, rest = "")
            return
        }
        settle(parse(s.substring(bodyStart + 1, close).trim()), rest = s.substring(close + FENCE.length))
    }

    private fun settle(g: Grounding, rest: String) {
        resolved = true
        grounding = g
        head.clear()
        onGrounding(g)
        if (g == Grounding.NotJson) emitText(rest) else emitText(rest.trimStart())
    }

    private fun emitText(chunk: String) {
        if (textOut.isEmpty()) {
            // Drop whitespace before the first word (the box line's newline) and a "Fixy:" speaker tag the
            // model sometimes adds; hold the start back until it's long enough to tell.
            lead.append(chunk)
            if (lead.trimStart().length >= SPEAKER_TAG_HOLD) flushLead()
            return
        }
        textOut.append(chunk)
        onText(chunk)
    }

    private fun flushLead() {
        val piece = lead.toString().trimStart().replaceFirst(SPEAKER_TAG, "")
        lead.clear()
        if (piece.isEmpty()) return
        textOut.append(piece)
        onText(piece)
    }

    companion object {
        private const val FENCE = "```"
        /** Give up waiting for the JSON to close after this many characters. */
        private const val MAX_HEAD = 600
        private const val SPEAKER_TAG_HOLD = 6
        private val SPEAKER_TAG = Regex("""^(?i:fixy)\s*:\s*""")
        private val json = Json { isLenient = true }

        /** Index of the bracket that closes the JSON value opening at [start], or -1 if not complete yet. */
        internal fun jsonEnd(s: String, start: Int): Int {
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until s.length) {
                val c = s[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> if (--depth == 0) return i
                }
            }
            return -1
        }

        internal fun parse(candidate: String): Grounding {
            val element = runCatching { json.parseToJsonElement(candidate) }.getOrNull() ?: return Grounding.Invalid
            return fromElement(element)
        }

        private fun fromElement(e: JsonElement): Grounding = when (e) {
            is JsonObject -> fromObject(e)
            is JsonArray -> when {
                e.isEmpty() -> Grounding.NoBox
                // A bare [x1,y1,x2,y2].
                e.size == 4 && e.all { it is JsonPrimitive } -> numbers(e)?.let { Grounding.Box(ModelBox(it[0], it[1], it[2], it[3])) }
                    ?: Grounding.Invalid
                // [{"bbox_2d":…}, …] or [[x1,y1,x2,y2]]: the first entry is the answer.
                else -> e.firstOrNull { it is JsonObject || it is JsonArray }?.let(::fromElement) ?: Grounding.Invalid
            }
            is JsonNull -> Grounding.NoBox
            else -> Grounding.Invalid
        }

        private fun fromObject(o: JsonObject): Grounding {
            val label = (o["label"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val bbox = o["bbox_2d"] ?: o["bbox"]
            if (bbox != null) {
                if (bbox is JsonNull) return Grounding.NoBox
                val n = (bbox as? JsonArray)?.let(::numbers)
                return if (n != null && n.size == 4) Grounding.Box(ModelBox(n[0], n[1], n[2], n[3], label)) else Grounding.Invalid
            }
            val point = o["point_2d"] ?: return Grounding.Invalid
            if (point is JsonNull) return Grounding.NoBox
            val p = (point as? JsonArray)?.let(::numbers)
            return if (p != null && p.size == 2) Grounding.Box(ModelBox(p[0], p[1], p[0], p[1], label, isPoint = true)) else Grounding.Invalid
        }

        private fun numbers(a: JsonArray): List<Float>? =
            a.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.floatOrNull ?: return null }
    }
}
