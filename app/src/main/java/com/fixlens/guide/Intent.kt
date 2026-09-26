package com.fixlens.guide

/**
 * What kind of question the user asked, from plain word rules (no VLM call). It decides what the turn sends:
 * small talk goes without a picture or a pointing request, "what do you see?" gets the picture but no pointing,
 * and everything else is a repair question that points at parts. Anything unclear counts as [Repair], so the
 * pointing loop is never lost by mistake.
 */
enum class Intent {
    /** Small talk: hi, thanks, who are you, what can you do. */
    Chat,
    /** Describe the picture: what do you see, what is this. */
    Look,
    /** A problem or a "where / how" question: point at the parts. */
    Repair;

    companion object {
        // Any of these makes it a repair question, even inside "what do you see".
        private val REPAIR = Regex(
            """\b(where|which|point|show me|find|locate|how (do|can|should|to|would)|fix|repair|check|open|remove|""" +
                """replace|clean|refill|top up|unscrew|tighten|loosen|error|code|light|blink|warning|won'?t|doesn'?t|""" +
                """isn'?t|not working|broken|leak|problem|issue|stuck|noise|smell|drain|overheat|help me)\b|""" +
                """\b(can|do) you see (the|a|an|any|my)\b""",
        )
        private val LOOK = Regex(
            """\b(what (do|can) you see|what are you seeing|what('?s| is) (this|that|it|in front|on the screen)|""" +
                """what am i (looking at|showing|pointing at|holding)|describe|tell me what you see|""" +
                """(can|do) you see (this|that|it)|look at (this|that)|identify)\b""",
        )
        private val CHAT = Regex(
            """^(hi|hello|hey|yo|good (morning|afternoon|evening|night)|thanks|thank you|thank u|cheers|ok|okay|""" +
                """cool|nice|great|awesome|perfect|bye|goodbye|see you|yes|yeah|yep|yup|no|nope|nah|sure)\b|""" +
                """\b(who are you|what('?s| is) your name|what (can|do) you do|what are you|how do you work|""" +
                """how are you|are you (a |an )?(robot|bot|ai|real|human)|what('?s| is) fixlens|what('?s| is) fixy)\b""",
        )
        /** Past this, a greeting word is just the start of a real question ("hi, my washer shows OE"). */
        private const val CHAT_MAX_WORDS = 8

        fun of(question: String): Intent {
            val q = question.lowercase().replace('’', '\'').trim()
            return when {
                REPAIR.containsMatchIn(q) -> Repair
                LOOK.containsMatchIn(q) -> Look
                CHAT.containsMatchIn(q) && q.split(Regex("""\s+""")).size <= CHAT_MAX_WORDS -> Chat
                else -> Repair
            }
        }
    }
}
