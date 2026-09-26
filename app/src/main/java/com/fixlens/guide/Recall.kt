package com.fixlens.guide

import com.fixlens.session.Outcome
import com.fixlens.session.RepairSession
import java.util.Calendar

/**
 * What Fixy remembers across sessions (see docs/fixy-memory.md). Built from the saved sessions with plain
 * rules, no VLM call, so it's instant and can't invent anything: a short `<past_repairs>` block for the system
 * prompt, and the templated greeting that opens a new session.
 */
object Recall {

    /** The earlier repairs a new or reopened session gets to see, newest first. */
    fun candidates(all: List<RepairSession>, currentId: String): List<RepairSession> =
        all.filter { it.id != currentId && it.turns.isNotEmpty() }
            .sortedByDescending { it.updated }
            .take(MAX_PAST)

    /**
     * One line per past repair, e.g.
     * `- "Washer not draining" (washing machine, LG, error OE; not draining), 2 days ago, fixed. Last advice: "…"`.
     * Null when there's nothing to remember.
     */
    fun pastRepairs(past: List<RepairSession>, now: Long = System.currentTimeMillis()): String? {
        if (past.isEmpty()) return null
        return past.joinToString("\n") { s ->
            val m = s.memory
            val facts = listOfNotNull(
                m.appliance?.lowercase(),
                m.brand,
                m.errorCode?.let { "error $it" },
            ).joinToString(", ") + m.symptoms.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "; ").orEmpty()
            val status = when (m.outcome) {
                Outcome.Fixed -> "fixed"
                Outcome.NotFixed -> "not fixed yet"
                Outcome.Unknown -> "outcome unknown"
            }
            val advice = s.turns.last().answer.let(::clip)
            buildString {
                append("- \"").append(s.title).append('"')
                if (facts.isNotBlank()) append(" (").append(facts.trimStart(';', ' ')).append(')')
                append(", ").append(ago(s.updated, now)).append(", ").append(status)
                append(". Last advice: \"").append(advice).append('"')
            }
        }
    }

    data class Greeting(val text: String, val followUpOf: String?)

    /**
     * Fixy's opening line for a new session. First ever: the fixed intro. Otherwise a time-of-day hello that
     * asks after the latest repair if it's recent and not known to be fixed, then asks what's on today.
     */
    fun greeting(past: List<RepairSession>, now: Long = System.currentTimeMillis()): Greeting {
        if (past.isEmpty()) return Greeting(FIRST_GREETING, null)
        val hello = hello(now)
        val last = past.first()
        val recent = now - last.updated < FOLLOW_UP_WINDOW_MS
        val thing = subject(last)
        return when {
            !recent -> Greeting("$hello, welcome back! What are we fixing today?", null)
            last.memory.outcome == Outcome.Fixed ->
                Greeting("$hello, welcome back! Glad $thing is sorted. What are we fixing today?", null)
            else -> {
                val detail = last.memory.errorCode?.let { " (error $it)" }
                    ?: last.memory.symptoms.firstOrNull()?.let { " ($it)" }.orEmpty()
                Greeting(
                    "$hello! Last time we looked at $thing$detail ${ago(last.updated, now)}. " +
                        "Is it working fine now? And what are we fixing today?",
                    last.id,
                )
            }
        }
    }

    /** "your washing machine", "your car", or the session's title when the device is unknown. */
    private fun subject(s: RepairSession): String = when (val a = s.memory.appliance) {
        null -> "\"${s.title}\""
        "Car engine" -> "your car"
        else -> "your ${a.lowercase()}"
    }

    private fun hello(now: Long): String = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hi there"
    }

    /** Calendar days, so a repair from last night reads "yesterday" even if it's under 24 h ago. */
    private fun ago(then: Long, now: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = then }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        val days = ((b.startOfDay() - a.startOfDay()) / DAY_MS).toInt()
        return when {
            days <= 0 -> "earlier today"
            days == 1 -> "yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "last week"
            days < 60 -> "${days / 7} weeks ago"
            else -> "a while ago"
        }
    }

    private fun Calendar.startOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }

    /** First sentence, cut at a word boundary. */
    private fun clip(text: String): String {
        val first = text.substringBefore(". ").trim()
        if (first.length <= MAX_ADVICE) return first.trimEnd('.')
        return first.take(MAX_ADVICE).substringBeforeLast(' ') + "…"
    }

    /** CLAUDE.md §8: the fixed first greeting. */
    const val FIRST_GREETING = "Hi, I'm Fixy! Point me at what's broken and tell me what's happening."

    /** ~35 tokens each; four keep a rebuild's extra prefill small. */
    private const val MAX_PAST = 4
    private const val MAX_ADVICE = 80
    private const val DAY_MS = 24L * 60 * 60 * 1000
    /** Past this, asking "is it working now?" would be odd. */
    private const val FOLLOW_UP_WINDOW_MS = 14 * DAY_MS
}
