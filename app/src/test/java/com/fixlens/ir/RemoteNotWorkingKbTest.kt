package com.fixlens.ir

import com.fixlens.guide.MemoryRules
import com.fixlens.kb.KbRepository
import com.fixlens.kb.Retriever
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * "My TV remote isn't working" must land on the KB's remote entry: with an IR blaster, that match starts the
 * device-or-remote test straight away (FixLensViewModel.handleGuide) instead of the battery guide.
 */
class RemoteNotWorkingKbTest {
    private val kb = KbRepository.parse(File("src/main/assets/kb/fixlens_kb.json").readText())
    private val retriever = Retriever(kb)

    private fun entryFor(text: String): String? {
        val (appliance, brand) = MemoryRules.kbContext(text, null)
        return (retriever.find(text, appliance, brand) as? Retriever.Match.Found)?.entry?.id
    }

    @Test
    fun `the user's own words find the tv remote entry`() {
        for (s in listOf(
            "my TV remote is not working",
            "the TV remote is not working",
            "my tv is not responding to the remote",
            "tv remote not working",
        )) {
            assertEquals(s, "television_remote_not_working", entryFor(s))
        }
    }
}
