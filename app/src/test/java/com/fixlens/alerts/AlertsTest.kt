package com.fixlens.alerts

import com.fixlens.guide.Command
import com.fixlens.guide.Guide
import com.fixlens.guide.GuideState
import com.fixlens.kb.KbEntry
import com.fixlens.kb.KbRemind
import com.fixlens.kb.KbRepository
import com.fixlens.kb.KbStep
import com.fixlens.kb.KnowledgeBase
import com.fixlens.kb.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlertsTest {

    private val oil = KbEntry(
        id = "car_check_engine_oil", appliance = "car", title = "Check and top up engine oil", meaning = "Let's check the oil.",
        severity = Severity.Diy, safety = listOf("Engine off."), steps = listOf(KbStep(1, "Pull out the dipstick.")),
        remind = KbRemind(7, "Time for your weekly engine oil check."), source = "test",
    )
    private val drain = oil.copy(id = "washing_machine_not_draining", appliance = "washing_machine", remind = null)
    private val now = 1_790_000_000_000L

    @Test fun `a finished routine check plans its next one from the KB`() {
        val a = Alerts.plan(oil, now, "v1", "s1", "Engine oil check")!!
        assertEquals("car_check_engine_oil", a.entryId)
        assertEquals("Time for your weekly engine oil check.", a.body)
        assertEquals(now + 7 * Alerts.DAY_MS, a.dueAt)
        assertEquals("Engine oil check", a.sessionTitle)
        assertEquals(false, a.delivered)
    }

    @Test fun `a repair that isn't a routine check schedules nothing`() {
        assertNull(Alerts.plan(drain, now, "v1", null, null))
    }

    @Test fun `doing the check again replaces the waiting reminder`() {
        val first = Alerts.plan(oil, now, "v1", null, null)!!
        val other = Alerts.plan(oil.copy(id = "car_check_coolant", remind = KbRemind(7, "Coolant.")), now, "v1", null, null)!!
        val again = Alerts.plan(oil, now + Alerts.DAY_MS, "v1", null, null)!!
        val list = Alerts.add(Alerts.add(Alerts.add(emptyList(), first), other), again)
        assertEquals(2, list.size)
        assertEquals(again.id, list.single { it.entryId == oil.id }.id)
    }

    @Test fun `due alerts come first, then the soonest`() {
        val a = Alerts.plan(oil, now, "v1", null, null)!!.copy(id = "a", dueAt = now + 5)
        val b = a.copy(id = "b", dueAt = now + 1)
        val c = a.copy(id = "c", dueAt = now + 9, delivered = true)
        assertEquals(listOf("c", "b", "a"), Alerts.ordered(listOf(a, b, c)).map { it.id })
    }

    @Test fun `intervals are said the way people say them`() {
        assertEquals("a week", Alerts.spokenInterval(7))
        assertEquals("two weeks", Alerts.spokenInterval(14))
        assertEquals("a month", Alerts.spokenInterval(30))
        assertEquals("three months", Alerts.spokenInterval(90))
        assertEquals("10 days", Alerts.spokenInterval(10))
        assertEquals("every week", Alerts.everyInterval(7))
        assertEquals("every two weeks", Alerts.everyInterval(14))
    }

    @Test fun `the guide's last line says when the reminder comes`() {
        var s: GuideState = Guide.start(oil)
        repeat(2) { s = Guide.onCommand(s, Command.Done).first }
        assertTrue(s is GuideState.Done)
        assertEquals("${Guide.ALL_DONE} I'll remind you to check it again in a week.", Guide.instruction(s)!!.say)
        assertEquals(Guide.ALL_DONE, Guide.instruction(GuideState.Done(drain))!!.say)
    }

    @Test fun `validation rejects bad reminders`() {
        fun problems(e: KbEntry) = KbRepository.problems(KnowledgeBase("v", listOf(e)))
        assertTrue(problems(oil).isEmpty())
        assertTrue(problems(oil.copy(remind = KbRemind(0, "Now."))).any { "after_days" in it })
        assertTrue(problems(oil.copy(remind = KbRemind(7, " "))).any { "nothing to say" in it })
        val tech = oil.copy(severity = Severity.CallTechnician, safety = emptyList(), steps = emptyList())
        assertTrue(problems(tech).any { "no reminder" in it })
    }

    @Test fun `the bundled KB schedules the car checks weekly`() {
        val kb = KbRepository.parse(File("src/main/assets/kb/fixlens_kb.json").readText())
        val oilEntry = kb.entries.single { it.id == "car_check_engine_oil" }
        assertEquals(7, oilEntry.remind?.afterDays)
        assertNotNull(kb.entries.single { it.id == "car_check_air_filter" }.remind)
        // Technician jobs never schedule a check.
        assertTrue(kb.entries.filter { it.severity == Severity.CallTechnician }.all { it.remind == null })
    }
}
