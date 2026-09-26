package com.fixlens.kb

/**
 * Finds the KB entry for what the user said (CLAUDE.md §7), cheapest and most exact first:
 * 1. **Exact code:** an error code in the text (normalized: uppercase, no spaces, O ≡ 0, plus spoken forms like
 *    "oh e") matched against each brand's codes (`brand_codes`, or an entry's own `error_code`).
 * 2. **Keywords:** how much of an entry's aliases, symptoms or title the text covers; accepted only when the best
 *    entry clearly beats the next one.
 * 3. (LLM router, later) 4. **None:** the caller refuses or answers without steps.
 * [appliance] (from the user's words or the session notes) narrows both stages.
 */
class Retriever(private val kb: KnowledgeBase) {

    sealed interface Match {
        data class Found(val entry: KbEntry, val stage: Int, val why: String) : Match
        data object None : Match
    }

    /** The brands the KB has codes for, so "my Samsung shows 5C" picks Samsung's code even with no notes yet. */
    private val codeBrands = kb.entries.flatMap { e -> e.codeForms().map { it.first } }.distinct().filter { it != GENERIC }

    fun find(text: String, appliance: String? = null, brand: String? = null): Match {
        val pool = kb.entries.filter { appliance == null || it.appliance == appliance }
        val said = " ${tokens(text).joinToString(" ")} "
        val brandSaid = codeBrands.firstOrNull { said.contains(" ${tokens(it).joinToString(" ")} ") } ?: brand
        return byCode(text, pool, brandSaid) ?: byKeywords(text, pool) ?: Match.None
    }

    /**
     * Stage 1. With a brand known, only that brand's codes (or brand-less ones) count; without one, the code must
     * lead to a single entry. A code of letters only ("OE", "dC"; zero counts as the letter O) also needs a cue
     * ("error", "showing"…) or a brand, so everyday words don't read as codes.
     */
    private fun byCode(text: String, pool: List<KbEntry>, brand: String?): Match? {
        val tokens = tokens(text)
        val codeTokens = tokens.map(Codes::normalize).toSet() +
            tokens.zipWithNext { a, b -> Codes.normalize(a + b) } // "O E" → "0E"
        val lower = " ${tokens.joinToString(" ")} "
        val cued = brand != null || tokens.any { it in CODE_CUES }
        fun said(form: String): Boolean =
            if (form.contains(' ')) lower.contains(" ${tokens(form).joinToString(" ")} ")
            else Codes.normalize(form).let { it.isNotEmpty() && it in codeTokens && (cued || it.any { c -> c in '1'..'9' }) }
        val hits = pool.flatMap { e ->
            e.codeForms().filter { (_, forms) -> forms.any(::said) }.map { (b, forms) -> Triple(e, b, forms.first()) }
        }
        val usable = if (brand == null) hits else hits.filter { it.second.equals(brand, true) || it.second == GENERIC }
        val entries = usable.map { it.first }.distinct()
        if (entries.size != 1) return null
        val (entry, codeBrand, code) = usable.first()
        return Match.Found(entry, 1, "code $code ($codeBrand)")
    }

    /** Stage 2: the share of an alias/symptom/title's words found in the text, best phrase per entry. */
    private fun byKeywords(text: String, pool: List<KbEntry>): Match? {
        val said = words(text).toSet()
        if (said.isEmpty()) return null
        val scored = pool.map { e ->
            val phrases = e.aliases + e.symptoms + e.title
            val best = phrases.maxOfOrNull { phrase ->
                val words = words(phrase)
                if (words.isEmpty()) 0.0 else words.count { it in said }.toDouble() / words.size
            } ?: 0.0
            e to best
        }.sortedByDescending { it.second }
        val (top, score) = scored.firstOrNull() ?: return null
        val runnerUp = scored.getOrNull(1)?.second ?: 0.0
        if (score < MIN_SCORE || score - runnerUp < MIN_MARGIN) return null
        return Match.Found(top, 2, "keywords %.2f (next %.2f)".format(score, runnerUp))
    }

    companion object {
        const val MIN_SCORE = 0.6
        const val MIN_MARGIN = 0.2
        /** An entry's own `error_code` under this brand matches whatever brand the user has. */
        const val GENERIC = "generic"
        private val STOPWORDS = setOf(
            "a", "an", "the", "i", "my", "me", "it", "is", "are", "to", "do", "how", "what", "where", "can",
            "this", "that", "of", "in", "on", "for", "with", "and", "or", "you", "your", "should", "please",
            "fixy", "hey", "hi", "so", "like", "just", "there", "here", "its", "be", "was", "have", "has",
        )
        /** Words that say a code follows or precedes: "error OE", "it's showing dC", "UE is blinking". */
        private val CODE_CUES = setOf(
            "error", "code", "shows", "showing", "show", "display", "displays", "displaying", "says", "saying",
            "blinking", "blinks", "flashing", "flashes", "screen", "reads",
        )
        /** "won't", "doesn't", "isn't", "no" all say "not", so "won't drain" matches "not draining". */
        private val NEGATIONS = setOf("not", "no", "never", "cannot", "wont", "dont", "doesnt", "isnt", "cant", "didnt", "arent")

        fun tokens(s: String): List<String> =
            s.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }

        /** Stage-2 words: negations unified ("won" + "t" → "not"), stemmed, stop words dropped. */
        fun words(s: String): List<String> {
            val out = mutableListOf<String>()
            for (t in tokens(s)) when {
                t == "t" && out.isNotEmpty() -> out[out.lastIndex] = "not"
                t in NEGATIONS -> out += "not"
                else -> out += t
            }
            return out.map(::stem).filter { it !in STOPWORDS }
        }

        /** Crude English stemming, enough for "checking"/"checked"/"checks" → "check". */
        fun stem(w: String): String = when {
            w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
            w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
            w.length > 4 && w.endsWith("es") -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            else -> w
        }
    }
}
