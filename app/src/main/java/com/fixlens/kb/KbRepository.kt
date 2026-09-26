package com.fixlens.kb

import android.content.Context
import kotlinx.serialization.json.Json

/** Loads and checks the knowledge base (CLAUDE.md §7). Parsing and validation are pure, so they're unit-tested. */
object KbRepository {

    const val ASSET = "kb/fixlens_kb.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): KnowledgeBase = json.decodeFromString(KnowledgeBase.serializer(), text)

    /** Reads the bundled KB. Call off the main thread. */
    fun load(context: Context): KnowledgeBase = parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })

    /**
     * Everything wrong with [kb] (empty = good): ids unique, severity with its rules, at least one safety line
     * unless it's a technician job, steps numbered 1..n, no repair steps for technician jobs, unique codes per
     * appliance and brand (none that read as ordinary words), spoken lines short enough to say.
     */
    fun problems(kb: KnowledgeBase): List<String> {
        val out = mutableListOf<String>()
        if (kb.version.isBlank()) out += "kb_version is missing"
        kb.entries.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { out += "duplicate id $it" }
        val codes = mutableMapOf<String, String>()
        for (e in kb.entries) {
            val at = "entry ${e.id}"
            if (e.id.isBlank()) out += "an entry has no id"
            if (e.title.isBlank()) out += "$at: no title"
            when (e.severity) {
                Severity.CallTechnician -> if (e.steps.isNotEmpty()) out += "$at: call_technician entries give no repair steps"
                else -> {
                    if (e.safety.isEmpty()) out += "$at: needs at least one safety line"
                    if (e.steps.isEmpty()) out += "$at: no steps"
                }
            }
            if (words(e.meaning) > MAX_LINE_WORDS) out += "$at: meaning is ${words(e.meaning)} words (max $MAX_LINE_WORDS)"
            e.safety.forEach { if (words(it) > MAX_LINE_WORDS) out += "$at: safety line is ${words(it)} words (max $MAX_LINE_WORDS): $it" }
            e.escalateIf.filter { sign -> Retriever.tokens(sign).none { it.length > 2 } }.forEach { out += "$at: escalate_if \"$it\" has no word to match" }
            e.steps.forEachIndexed { i, s ->
                if (s.n != i + 1) out += "$at: step ${s.n} should be number ${i + 1}"
                if (s.say.isBlank()) out += "$at: step ${s.n} has nothing to say"
                if (words(s.say) > MAX_SAY_WORDS) out += "$at: step ${s.n} is ${words(s.say)} words (max $MAX_SAY_WORDS)"
                if (s.target != null && s.target.isBlank()) out += "$at: step ${s.n} has a blank target"
            }
            if (e.source.isBlank()) out += "$at: no source"
            e.brandCodes.filter { it.brand.isBlank() || it.codes.isEmpty() }.forEach { out += "$at: a brand_codes item needs a brand and codes" }
            for ((brand, forms) in e.codeForms()) for (code in forms.filter { !it.contains(' ') }) {
                val norm = Codes.normalize(code)
                if (norm in WORD_CODES) out += "$at: code $code reads as an ordinary word"
                val key = "${e.appliance}|${brand.lowercase()}|$norm"
                val other = codes.put(key, e.id)
                if (other != null && other != e.id) out += "code $code ($brand) of ${e.id} clashes with $other"
            }
        }
        return out
    }

    private fun words(s: String) = s.split(Regex("""\s+""")).count { it.isNotEmpty() }

    /** CLAUDE.md §7 / kb-collection-plan.md: one action per step, speakable in one breath. */
    const val MAX_SAY_WORDS = 20
    /** `meaning` and safety lines are spoken too, a little longer is fine. */
    const val MAX_LINE_WORDS = 25
    /** Normalized codes that are everyday words ("AC" the appliance, "ON"), so they'd match ordinary speech. */
    private val WORD_CODES = setOf("AC", "TV", "0N", "N0", "0K", "IF", "IS", "IT", "AT", "AN", "AS", "BE", "D0", "G0", "UP", "US", "WE", "MY", "BY", "T0", "S0", "0R", "0F", "IN", "A", "I")
}

/** Error-code normalization shared by validation and retrieval: uppercase, no spaces or dashes, letter O ≡ zero. */
object Codes {
    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }.replace('O', '0')
}
