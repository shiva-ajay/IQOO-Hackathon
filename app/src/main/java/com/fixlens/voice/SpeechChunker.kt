package com.fixlens.voice

/**
 * Cuts Fixy's streamed reply into pieces the voice can start saying while the rest is still being generated.
 * Supertonic synthesizes each piece whole (it can't stream inside one), so the first piece of a reply is kept
 * short (its first clause) to get audio out early; later pieces are whole sentences, synthesized while the
 * previous one plays. The caption keeps the raw text; only what is spoken goes through here.
 * Not thread-safe: one per reply, fed from one thread (like GroundingParser).
 */
class SpeechChunker {

    /** A piece to speak; [sentenceEnd] asks for a sentence-length pause after it (else a clause-length one). */
    data class Piece(val text: String, val sentenceEnd: Boolean)

    private class Cut(val end: Int, val sentenceEnd: Boolean)

    private val buf = StringBuilder()
    private var emitted = 0

    fun feed(chunk: String): List<Piece> {
        buf.append(chunk)
        val out = mutableListOf<Piece>()
        while (true) {
            val cut = nextCut() ?: break
            take(cut.end, cut.sentenceEnd)?.let(out::add)
        }
        return out
    }

    /** The end of the reply: whatever is left. */
    fun flush(): Piece? = take(buf.length, sentenceEnd = true)

    private fun take(end: Int, sentenceEnd: Boolean): Piece? {
        val raw = buf.substring(0, end)
        buf.delete(0, end)
        val text = clean(raw)
        if (text.none { it.isLetterOrDigit() }) return null
        emitted++
        return Piece(text, sentenceEnd)
    }

    /** The first place the buffer can be cut (never past [HARD_MAX]), or null to wait for more text. */
    private fun nextCut(): Cut? {
        for (i in 0 until minOf(buf.length, HARD_MAX)) {
            val c = buf[i]
            val next = buf.getOrNull(i + 1)
            when {
                c == '\n' -> if (buf.substring(0, i).isNotBlank()) return Cut(i + 1, true)
                c in ENDS -> {
                    // A closing quote or bracket belongs to the sentence; the space after it confirms the end
                    // (so "2.5" and "e.g." don't cut). At the very end of the buffer, wait for the next chunk.
                    var j = i + 1
                    while (j < buf.length && buf[j] in CLOSERS) j++
                    if (j < buf.length && buf[j].isWhitespace() && !isAbbreviationOrListMark(i)) return Cut(j, true)
                }
                (c == ';' || c == ':') && next?.isWhitespace() == true -> return Cut(i + 1, false)
                emitted == 0 && c in CLAUSE && next?.isWhitespace() == true &&
                    words(buf.substring(0, i)) >= FIRST_CLAUSE_WORDS -> return Cut(i + 1, false)
            }
        }
        if (buf.length >= SOFT_MAX) {
            val comma = buf.lastIndexOf(", ", HARD_MAX - 1)
            if (comma > 0) return Cut(comma + 1, false)
        }
        if (buf.length >= HARD_MAX) {
            val space = buf.lastIndexOf(" ", HARD_MAX)
            return Cut(if (space > 0) space else HARD_MAX, false)
        }
        return null
    }

    /** The "." at [dot] ends "e.g", "Mr" and the like, or a "1." list mark at the start of a line. */
    private fun isAbbreviationOrListMark(dot: Int): Boolean {
        if (buf[dot] != '.' || buf.getOrNull(dot - 1) == '.') return false // an ellipsis ends a phrase
        var start = dot
        while (start > 0 && (buf[start - 1].isLetterOrDigit() || buf[start - 1] == '.')) start--
        val word = buf.substring(start, dot).lowercase()
        if (word.isEmpty()) return false
        if (word in ABBREVIATIONS || '.' in word) return true
        val lineStart = buf.lastIndexOf("\n", start - 1) + 1
        return word.all { it.isDigit() } && buf.substring(lineStart, start).isBlank()
    }

    companion object {
        /** The first piece may end at a comma once it has this many words. */
        const val FIRST_CLAUSE_WORDS = 4
        /** A piece this long with no sentence end is cut at its last comma… */
        const val SOFT_MAX = 100
        /** …and at its last space from here. */
        const val HARD_MAX = 160

        private const val ENDS = ".!?"
        private const val CLOSERS = "\"')]”’"
        private const val CLAUSE = ",—–"
        private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "vs", "st", "approx")

        /** Pieces of a whole text (KB lines, the greeting), cut the same way as a streamed reply. */
        fun split(text: String): List<Piece> {
            val chunker = SpeechChunker()
            return chunker.feed(text) + listOfNotNull(chunker.flush())
        }

        private val WORD = Regex("""\S+""")
        private val LINE_MARK = Regex("""(?m)^\s*(?:#+|[-*•]|\d+[.)])\s+""")
        private val MARKUP = Regex("""[*_`]+""")
        private val SPACES = Regex("""\s+""")
        private val SPACE_BEFORE_MARK = Regex(""" ([.,!?;:])""")

        private fun words(s: String) = WORD.findAll(s).count()

        /**
         * What Supertonic should read: no markdown (its frontend reads "*" aloud), no emoji or symbols it can't
         * say, "&" and "%" as words, whitespace collapsed.
         */
        fun clean(raw: String): String {
            val s = raw
                .replace(LINE_MARK, "")
                .replace(MARKUP, " ")
                .replace("&", " and ")
                .replace("%", " percent")
                .replace("°C", " degrees Celsius")
                .replace("°", " degrees")
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                i += Character.charCount(cp)
                if (!speakable(cp)) continue
                out.appendCodePoint(cp)
            }
            return out.toString().replace(SPACES, " ").replace(SPACE_BEFORE_MARK, "$1").trim()
        }

        /** Emoji, pictographs, dingbats and joiners are dropped (the voice's text index covers the BMP only). */
        private fun speakable(cp: Int): Boolean = when {
            cp > 0xFFFF -> false
            cp in 0x2600..0x27BF -> false // misc symbols, dingbats (✓, ☀)
            cp in 0xFE00..0xFE0F || cp == 0x200D -> false // variation selectors, zero-width joiner
            Character.getType(cp) == Character.OTHER_SYMBOL.toInt() -> false
            else -> true
        }
    }
}
