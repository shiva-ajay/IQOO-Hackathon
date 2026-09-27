package com.fixlens.alerts

import com.fixlens.kb.KbEntry
import kotlinx.serialization.Serializable

/**
 * A reminder Fixy scheduled by itself: a routine check (engine oil, tyre pressure…) finished in a guide comes round
 * again after the KB entry's `remind.after_days`. The words are the KB's, shown verbatim; nothing is generated.
 */
@Serializable
data class Alert(
    val id: String,
    /** The KB entry whose guide finished; the reminder's "Start the check" opens it again. */
    val entryId: String,
    /** The entry's title ("Check and top up engine oil"). */
    val title: String,
    /** The entry's `remind.say`, verbatim. */
    val body: String,
    val appliance: String,
    val afterDays: Int,
    val createdAt: Long,
    val dueAt: Long,
    /** The repair session it came from, and its title then (the session may be renamed or deleted later). */
    val sessionId: String? = null,
    val sessionTitle: String? = null,
    val kbVersion: String? = null,
    /** The notification went out (or its time passed): shown under "Due now" until started or dismissed. */
    val delivered: Boolean = false,
)

/** Pure planning rules for alerts, unit-tested. The Android side is [AlertScheduler]. */
object Alerts {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** The reminder a finished guide for [entry] asks for, or null when the entry isn't a routine check. */
    fun plan(entry: KbEntry, now: Long, kbVersion: String?, sessionId: String?, sessionTitle: String?): Alert? {
        val remind = entry.remind ?: return null
        return Alert(
            id = "${entry.id}-$now",
            entryId = entry.id,
            title = entry.title,
            body = remind.say,
            appliance = entry.appliance,
            afterDays = remind.afterDays,
            createdAt = now,
            dueAt = now + remind.afterDays * DAY_MS,
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            kbVersion = kbVersion,
        )
    }

    /**
     * [alerts] with [alert] added. One reminder per check: doing the check again replaces the one still waiting
     * (and a "due" one for the same check, since it's just been done).
     */
    fun add(alerts: List<Alert>, alert: Alert): List<Alert> =
        (alerts.filterNot { it.entryId == alert.entryId } + alert).sortedBy { it.dueAt }

    /** Due now (delivered) first, oldest due first; then upcoming, soonest first. */
    fun ordered(alerts: List<Alert>): List<Alert> = alerts.sortedWith(compareBy({ !it.delivered }, { it.dueAt }))

    /** "two weeks", "a month": how Fixy says the interval when it schedules one. */
    fun spokenInterval(days: Int): String = NAMED[days] ?: when {
        days % 7 == 0 && days / 7 in 2..8 -> "${NUMBERS[days / 7]} weeks"
        days % 30 == 0 && days / 30 in 2..11 -> "${NUMBERS[days / 30]} months"
        else -> "$days days"
    }

    /** "every week", "every two weeks": how often it comes round. */
    fun everyInterval(days: Int): String = "every " + spokenInterval(days).removePrefix("a ")

    private val NAMED = mapOf(1 to "a day", 7 to "a week", 30 to "a month", 31 to "a month", 365 to "a year")
    private val NUMBERS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
}
