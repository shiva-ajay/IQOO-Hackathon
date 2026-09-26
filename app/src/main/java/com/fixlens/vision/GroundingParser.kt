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
 * Splits the VLM's streamed reply into the pointing JSON (first) and the spoken text (after it), as chunks arrive.
 *
 * The reply should start with JSON: one part `{"bbox_2d":[x1,y1,x2,y2],"label":"…"}` / `{"point_2d":[x,y],…}`,
 * or a list of parts `[{…}, {…}]` (every screw of a cover), or `{"bbox_2d":null}` / `[]` for nothing, optionally
 * inside a ```json fence and over several lines. Each part goes to [onTarget] the moment its own braces close,
 * while the model is still writing the rest of the list. [onGrounding] fires once when the JSON is complete.
 * Everything after it goes to [onText]. If the reply doesn't start with JSON, all of it is text (CLAUDE.md §9).
 * JSON that never completes is dropped (a list keeps the parts that did complete), so it never reaches captions.
 *
 * Not thread-safe: feed it from one thread (the VLM callback).
 */
class GroundingParser(
    private val onGrounding: (Grounding) -> Unit,
    private val onText: (String) -> Unit,
    private val onTarget: (ModelBox) -> Unit = {},
) {
    sealed interface Grounding {
        /** One or more parts to point at, in the order the model gave them. */
        data class Targets(val boxes: List<ModelBox>) : Grounding
        /** The model answered `null` / `[]`: nothing to point at. */
        data object NoBox : Grounding
        /** The reply didn't start with JSON: the format wasn't followed. */
        data object NotJson : Grounding
        /** It started like JSON but was cut off or unreadable. */
        data object Invalid : Grounding
    }

    private val head = StringBuilder()
    private val textOut = StringBuilder()
    /** The answer's first characters, held back until we know whether they're a fence end or "Fixy:" tag. */
    private val lead = StringBuilder()
    private var resolved = false
    private val targets = ArrayList<ModelBox>()

    // Incremental JSON scan over [head].
    private var jsonStart = -1
    private var fenced = false
    private var isList = false
    private var pos = 0
    private var depth = 0
    private var inString = false
    private var escaped = false
    private var elementStart = -1
    /** Where a top-level object ended, while we wait to see if a comma (an unbracketed list) follows. */
    private var objectEnd = -1
    /** A list the model wrote as "{…},{…}]" without the opening bracket. */
    private var bareList = false

    /** The spoken text so far (never includes the JSON). */
    val text: String get() = textOut.toString()

    var grounding: Grounding? = null
        private set

    fun feed(chunk: String) {
        if (resolved) {
            emitText(chunk)
            return
        }
        head.append(chunk)
        scan(final = false)
    }

    /** Call when the stream ends, so a reply whose JSON never completed is still resolved. */
    fun finish() {
        if (!resolved) scan(final = true)
        if (lead.isNotEmpty()) flushLead()
    }

    private fun scan(final: Boolean) {
        val s = head
        if (jsonStart < 0 && !findJsonStart(final)) return
        if (objectEnd >= 0 && !afterObject(final)) return
        while (pos < s.length) {
            val c = s[pos]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                pos++
                continue
            }
            if (bareList && depth == 1 && !c.isWhitespace() && c != ',' && c != '{' && c != '[' && c != ']') {
                finishJson("", rest = s.substring(pos)) // the unbracketed list ended without "]"
                return
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (isList && depth == 2) elementStart = pos
                }
                '}', ']' -> {
                    depth--
                    if (isList && depth == 1 && elementStart >= 0) {
                        elementBoxes(s.substring(elementStart, pos + 1)).forEach(::addTarget)
                        elementStart = -1
                    }
                    if (depth == 0 && !isList) {
                        // One object: its part goes out now; then see whether more follow ("{…},{…}]").
                        elementBoxes(s.substring(jsonStart, pos + 1)).forEach(::addTarget)
                        objectEnd = pos
                        pos++
                        if (!afterObject(final)) return
                        continue
                    }
                    if (depth == 0) {
                        finishJson(s.substring(jsonStart, pos + 1), rest = s.substring(pos + 1))
                        return
                    }
                }
            }
            pos++
        }
        if (final || s.length - jsonStart > MAX_JSON) {
            // Cut off (token limit) or runaway: keep the parts that completed, drop the rest of the JSON.
            settle(if (targets.isNotEmpty()) Grounding.Targets(targets.toList()) else Grounding.Invalid, rest = "")
        }
    }

    /**
     * After a top-level object: a comma means the model is writing an unbracketed list, so keep scanning as a list
     * (returns true); anything else ends the JSON (returns false). Waits (false) until the next character arrives.
     */
    private fun afterObject(final: Boolean): Boolean {
        val s = head
        val next = (objectEnd + 1 until s.length).firstOrNull { !s[it].isWhitespace() }
        if (next == null) {
            if (final) finishJson(s.substring(jsonStart, objectEnd + 1), rest = "")
            return false
        }
        if (s[next] != ',') {
            finishJson(s.substring(jsonStart, objectEnd + 1), rest = s.substring(objectEnd + 1))
            return false
        }
        isList = true
        bareList = true
        depth = 1
        objectEnd = -1
        pos = next + 1
        return true
    }

    /** Finds where the JSON begins (after an optional fence line). False while undecided or once settled as text. */
    private fun findJsonStart(final: Boolean): Boolean {
        val s = head
        val start = s.indexOfFirst { !it.isWhitespace() }
        if (start < 0) {
            if (final) settle(Grounding.NotJson, rest = "")
            return false
        }
        var at = start
        if (s.startsWith(FENCE, start)) {
            val nl = s.indexOf('\n', start)
            val body = if (nl >= 0) (nl until s.length).firstOrNull { !s[it].isWhitespace() } ?: -1 else -1
            if (body < 0) {
                if (final) settle(Grounding.Invalid, rest = "")
                return false
            }
            fenced = true
            at = body
        } else if (s.length - start < FENCE.length && FENCE.startsWith(s.substring(start)) && !final) {
            return false // a fence may still be forming ("`" or "``" so far)
        }
        if (s[at] != '{' && s[at] != '[') {
            settle(Grounding.NotJson, rest = s.toString())
            return false
        }
        jsonStart = at
        isList = s[at] == '['
        pos = at
        return true
    }

    private fun finishJson(json: String, rest: String) {
        // Parts already streamed out of a list; otherwise read the whole value ([] / null / one part / bare box).
        val g = if (targets.isNotEmpty()) {
            Grounding.Targets(targets.toList())
        } else {
            when (val parsed = parse(json)) {
                is Grounding.Targets -> {
                    parsed.boxes.forEach(::addTarget)
                    Grounding.Targets(targets.toList())
                }
                else -> parsed
            }
        }
        settle(g, rest.trimStart())
    }

    private fun addTarget(box: ModelBox) {
        if (targets.size >= MAX_TARGETS) return
        targets += box
        onTarget(box)
    }

    private fun settle(g: Grounding, rest: String) {
        resolved = true
        grounding = g
        head.clear()
        onGrounding(g)
        emitText(rest)
    }

    private fun emitText(chunk: String) {
        if (textOut.isEmpty()) {
            // Drop whitespace before the first word, a closing ``` still to come after the JSON, and a "Fixy:"
            // speaker tag the model sometimes adds; hold the start back until it's long enough to tell.
            lead.append(chunk)
            if (lead.trimStart().length >= LEAD_HOLD) flushLead()
            return
        }
        textOut.append(chunk)
        onText(chunk)
    }

    private fun flushLead() {
        var piece = lead.toString().trimStart()
        lead.clear()
        if (grounding != Grounding.NotJson) piece = piece.replaceFirst(FENCE_END, "")
        piece = piece.replaceFirst(SPEAKER_TAG, "")
        if (piece.isEmpty()) return
        textOut.append(piece)
        onText(piece)
    }

    companion object {
        private const val FENCE = "```"
        /** Give up on JSON that grows past this without closing. */
        private const val MAX_JSON = 4000
        /** More parts than this are ignored (runaway lists). */
        const val MAX_TARGETS = 16
        private const val LEAD_HOLD = 12
        private val FENCE_END = Regex("""^```\s*""")
        private val SPEAKER_TAG = Regex("""^(?i:fixy)\s*:\s*""")
        private val json = Json { isLenient = true }

        internal fun parse(candidate: String): Grounding {
            val element = runCatching { json.parseToJsonElement(candidate) }.getOrNull() ?: return Grounding.Invalid
            return when (element) {
                is JsonNull -> Grounding.NoBox
                is JsonArray -> if (element.isEmpty()) Grounding.NoBox else boxesOf(element).toGrounding()
                is JsonObject -> if (isNullAnswer(element)) Grounding.NoBox else boxesOf(element).toGrounding()
                else -> Grounding.Invalid
            }
        }

        /** The parts in one list element (usually one; nothing if it isn't a readable part). */
        private fun elementBoxes(candidate: String): List<ModelBox> =
            runCatching { json.parseToJsonElement(candidate) }.getOrNull()?.let(::boxesOf).orEmpty()

        private fun List<ModelBox>.toGrounding() = if (isEmpty()) Grounding.Invalid else Grounding.Targets(this)

        private fun isNullAnswer(o: JsonObject) =
            (o["bbox_2d"] ?: o["bbox"] ?: o["point_2d"]) is JsonNull

        private fun boxesOf(e: JsonElement): List<ModelBox> = when (e) {
            // A part, or a wrapper like {"points": [{…}, …]}.
            is JsonObject -> fromObject(e)?.let(::listOf) ?: e.values.filterIsInstance<JsonArray>().flatMap(::boxesOf)
            // A bare [x1,y1,x2,y2], or a list of parts.
            is JsonArray -> if (e.size == 4 && e.all { it is JsonPrimitive }) {
                listOfNotNull(numbers(e)?.let { ModelBox(it[0], it[1], it[2], it[3]) })
            } else {
                e.flatMap(::boxesOf).take(MAX_TARGETS)
            }
            else -> emptyList()
        }

        private fun fromObject(o: JsonObject): ModelBox? {
            val label = (o["label"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            (o["bbox_2d"] ?: o["bbox"])?.let { bbox ->
                val n = (bbox as? JsonArray)?.let(::numbers)
                return if (n != null && n.size == 4) ModelBox(n[0], n[1], n[2], n[3], label) else null
            }
            val p = (o["point_2d"] as? JsonArray)?.let(::numbers)
            return if (p != null && p.size == 2) ModelBox(p[0], p[1], p[0], p[1], label, isPoint = true) else null
        }

        private fun numbers(a: JsonArray): List<Float>? =
            a.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.floatOrNull ?: return null }
    }
}
