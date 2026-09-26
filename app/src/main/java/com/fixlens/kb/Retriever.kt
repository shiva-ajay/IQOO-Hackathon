package com.fixlens.kb

/**
 * Finds the KB entry for what the user said (CLAUDE.md §7), cheapest and most exact first:
 * 1. **Exact code:** an error code in the text (normalized: uppercase, no spaces, O ≡ 0, plus `code_aliases`,
 *    including spoken ones like "oh e") → `appliance|brand|code`.
 * 2. **Keywords:** how much of an entry's aliases, symptoms or title the text covers; accepted only when the best
 *    entry clearly beats the next one.
 * 3. (LLM router, later) 4. **None:** the caller refuses or answers without steps.
 * [appliance] (from the session notes, when known) narrows both stages.
 */
class Retriever(private val kb: KnowledgeBase) {

    sealed interface Match {
        data class Found(val entry: KbEntry, val stage: Int, val why: String) : Match
        data object None : Match
    }

    fun find(text: String, appliance: String? = null, brand: String? = null): Match {
        val pool = kb.entries.filter { appliance == null || it.appliance == appliance }
        return byCode(text, pool, brand) ?: byKeywords(text, pool) ?: Match.None
    }

    /** Stage 1. Ambiguous codes (the same code on two brands and no brand known) fall through to stage 2. */
    private fun byCode(text: String, pool: List<KbEntry>, brand: String?): Match? {
        val tokens = tokens(text)
        val codeTokens = tokens.map(Codes::normalize).toSet() +
            tokens.zipWithNext { a, b -> Codes.normalize(a + b) } // "O E" → "0E"
        val lower = " ${tokens.joinToString(" ")} "
        val hits = pool.filter { e ->
            val forms = listOfNotNull(e.errorCode) + e.codeAliases
            forms.any { form ->
                if (form.contains(' ')) lower.contains(" ${tokens(form).joinToString(" ")} ")
                else Codes.normalize(form).let { it.isNotEmpty() && it in codeTokens }
            }
        }
        val chosen = when {
            hits.isEmpty() -> return null
            hits.size == 1 -> hits.single()
            brand != null -> hits.singleOrNull { it.brand.equals(brand, ignoreCase = true) } ?: return null
            else -> return null
        }
        return Match.Found(chosen, 1, "code ${chosen.errorCode}")
    }

    /** Stage 2: the share of an alias/symptom/title's words found in the text, best phrase per entry. */
    private fun byKeywords(text: String, pool: List<KbEntry>): Match? {
        val said = tokens(text).map(::stem).filter { it !in STOPWORDS }.toSet()
        if (said.isEmpty()) return null
        val scored = pool.map { e ->
            val phrases = e.aliases + e.symptoms + e.title
            val best = phrases.maxOfOrNull { phrase ->
                val words = tokens(phrase).map(::stem).filter { it !in STOPWORDS }
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
        private val STOPWORDS = setOf(
            "a", "an", "the", "i", "my", "me", "it", "is", "are", "to", "do", "how", "what", "where", "can",
            "this", "that", "of", "in", "on", "for", "with", "and", "or", "you", "your", "should", "please",
            "fixy", "hey", "hi", "so", "like", "just", "there", "here", "its", "be", "was", "have", "has",
        )

        fun tokens(s: String): List<String> =
            s.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }

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
