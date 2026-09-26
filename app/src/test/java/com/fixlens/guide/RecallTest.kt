package com.fixlens.guide

import com.fixlens.session.Outcome
import com.fixlens.session.RepairSession
import com.fixlens.session.SessionMemory
import com.fixlens.session.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecallTest {

    /** 7 pm on a fixed day, so the time-of-day hello is stable. */
    private val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 27, 19, 0, 0) }.timeInMillis
    private val day = 24L * 60 * 60 * 1000

    private fun session(
        id: String,
        daysAgo: Int,
        memory: SessionMemory = SessionMemory(),
        title: String = "Washer not draining",
        answer: String = "Clean the drain filter at the bottom front. Then run a drain cycle.",
    ) = RepairSession(
        id = id, title = title, created = now - daysAgo * day, updated = now - daysAgo * day, memory = memory,
        turns = listOf(Turn(1, "It shows OE", answer, ts = now - daysAgo * day)),
    )

    @Test
    fun firstEverSessionGetsTheFixedIntro() {
        val g = Recall.greeting(emptyList(), now)
        assertEquals(Recall.FIRST_GREETING, g.text)
        assertNull(g.followUpOf)
    }

    @Test
    fun recentUnresolvedRepairIsAskedAbout() {
        val past = listOf(session("a", 1, SessionMemory(appliance = "Washing machine", errorCode = "OE")))
        val g = Recall.greeting(past, now)
        assertEquals(
            "Good evening! Last time we looked at your washing machine (error OE) yesterday. " +
                "Is it working fine now? And what are we fixing today?",
            g.text,
        )
        assertEquals("a", g.followUpOf)
    }

    @Test
    fun fixedRepairIsCelebratedNotAskedAbout() {
        val past = listOf(session("a", 2, SessionMemory(appliance = "Car engine", outcome = Outcome.Fixed)))
        val g = Recall.greeting(past, now)
        assertEquals("Good evening, welcome back! Glad your car is sorted. What are we fixing today?", g.text)
        assertNull(g.followUpOf)
    }

    @Test
    fun oldRepairIsNotAskedAbout() {
        val g = Recall.greeting(listOf(session("a", 30, SessionMemory(appliance = "Printer"))), now)
        assertEquals("Good evening, welcome back! What are we fixing today?", g.text)
        assertNull(g.followUpOf)
    }

    @Test
    fun unknownDeviceFallsBackToTheTitle() {
        val g = Recall.greeting(listOf(session("a", 0, title = "Laptop screen issue")), now)
        assertTrue(g.text, g.text.contains("\"Laptop screen issue\" earlier today"))
    }

    @Test
    fun pastRepairsLineHasFactsWhenStatusAndFirstSentenceOfAdvice() {
        val past = listOf(session("a", 3, SessionMemory("Washing machine", "LG", "OE", listOf("not draining"))))
        assertEquals(
            "- \"Washer not draining\" (washing machine, LG, error OE; not draining), 3 days ago, outcome unknown. " +
                "Last advice: \"Clean the drain filter at the bottom front\"",
            Recall.pastRepairs(past, now),
        )
    }

    @Test
    fun candidatesSkipTheCurrentAndEmptySessionsNewestFirst() {
        val empty = session("e", 0).copy(turns = emptyList())
        val list = Recall.candidates(listOf(session("old", 5), session("cur", 0), empty, session("new", 1)), "cur")
        assertEquals(listOf("new", "old"), list.map { it.id })
    }

    @Test
    fun outcomeReadsTheUsersWords() {
        assertEquals(Outcome.Fixed, MemoryRules.outcome("Thanks, it's working fine now"))
        assertEquals(Outcome.NotFixed, MemoryRules.outcome("It's still not draining"))
        assertEquals(Outcome.NotFixed, MemoryRules.outcome("no, it's not working fine"))
        assertNull(MemoryRules.outcome("What does OE mean?"))
    }

    @Test
    fun followUpReplyAcceptsPlainYesAndNo() {
        assertEquals(Outcome.Fixed, MemoryRules.followUpOutcome("Yeah! Today my printer is jammed"))
        assertEquals(Outcome.NotFixed, MemoryRules.followUpOutcome("Nope. Can you look again?"))
        assertEquals(Outcome.Fixed, MemoryRules.followUpOutcome("No worries, it works great now"))
        assertNull(MemoryRules.followUpOutcome("My fridge is making a noise"))
    }
}
