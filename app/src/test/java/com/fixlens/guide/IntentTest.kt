package com.fixlens.guide

import com.fixlens.session.Outcome
import com.fixlens.session.RepairSession
import com.fixlens.session.SessionMemory
import com.fixlens.session.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentTest {

    private fun assertIntent(expected: Intent, vararg questions: String) =
        questions.forEach { assertEquals(it, expected, Intent.of(it)) }

    @Test
    fun smallTalkIsChat() = assertIntent(
        Intent.Chat,
        "Hi", "hello Fixy", "Thanks!", "who are you", "What do you do?", "what can you do", "how are you doing",
        "Yes", "yeah it's fine now", "okay cool",
    )

    @Test
    fun describingThePictureIsLook() = assertIntent(
        Intent.Look,
        "What do you see?", "what can you see", "What's this?", "what am I looking at", "describe this", "can you see this",
    )

    @Test
    fun problemsAndWhereQuestionsPoint() = assertIntent(
        Intent.Repair,
        "where is the oil cap", "how do I check the coolant", "my washing machine shows OE",
        "hi, my laptop won't turn on", "what do you see, which one is the dipstick", "can you see the battery",
        "what's this error", "yes but it's still not working", "the water isn't draining",
    )

    @Test
    fun anythingUnclearPoints() = assertIntent(Intent.Repair, "the thing next to the fan", "hmm")

    @Test
    fun followUpReplyKeepsOnlyWhatsNew() {
        val car = SessionMemory(appliance = "Car engine", brand = "Maruti", symptoms = listOf("warning light"))
        // "Yes, the car's fine" is about the earlier repair: nothing of it becomes this session's notes.
        val same = MemoryRules.afterFollowUp(SessionMemory(), "yes the car is running fine now, the warning light is gone", car)
        assertNull(same.appliance)
        assertNull(same.brand)
        assertTrue(same.symptoms.isEmpty())
        assertEquals(Outcome.Unknown, same.outcome)
        // Something new in the same breath is kept.
        val new = MemoryRules.afterFollowUp(SessionMemory(), "yes it's fixed, but now my laptop won't turn on", car)
        assertEquals("Laptop", new.appliance)
        assertEquals(listOf("won't start"), new.symptoms)
    }

    @Test
    fun openedWithFollowUpNeedsAnAnswerToTheGreeting() {
        fun session(followUpOf: String?, first: String) =
            RepairSession(id = "b", created = 0, updated = 0, followUpOf = followUpOf, turns = listOf(Turn(1, first, "Great!", ts = 0)))
        assertTrue(MemoryRules.openedWithFollowUp(session("a", "yes it's working now")))
        assertFalse(MemoryRules.openedWithFollowUp(session("a", "what do you see")))
        assertFalse(MemoryRules.openedWithFollowUp(session(null, "yes it's working now")))
    }
}
