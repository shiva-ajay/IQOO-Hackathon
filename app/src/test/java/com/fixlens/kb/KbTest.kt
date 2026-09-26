package com.fixlens.kb

import com.fixlens.kb.Retriever.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KbTest {

    private val bundled = KbRepository.parse(File("src/main/assets/kb/fixlens_kb.json").readText())

    private fun entry(
        id: String, appliance: String = "front_load_washer", brand: String = "LG", code: String? = null,
        codeAliases: List<String> = emptyList(), aliases: List<String> = emptyList(), severity: Severity = Severity.Diy,
    ) = KbEntry(
        id = id, appliance = appliance, brand = brand, errorCode = code, codeAliases = codeAliases, title = id,
        aliases = aliases, severity = severity, safety = listOf("Unplug it."),
        steps = if (severity == Severity.CallTechnician) emptyList() else listOf(KbStep(1, "Do it.")), source = "test",
    )

    private val washer = KnowledgeBase(
        "t",
        listOf(
            entry("lg_oe", code = "OE", codeAliases = listOf("0E", "oh e", "zero e"), aliases = listOf("water not draining")),
            entry("lg_ie", code = "IE", codeAliases = listOf("I E"), aliases = listOf("no water coming in")),
            entry("sam_4c", brand = "Samsung", code = "4C", codeAliases = listOf("4E"), aliases = listOf("water supply error")),
            entry("sam_oe_clash", brand = "Samsung", code = "OE"),
        ),
    )

    @Test fun `the bundled KB is valid`() {
        val problems = KbRepository.problems(bundled)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun `validation catches the rules from CLAUDE md`() {
        val bad = KnowledgeBase(
            "",
            listOf(
                entry("a").copy(safety = emptyList()),
                entry("a").copy(steps = listOf(KbStep(2, "x"))),
                entry("t", severity = Severity.CallTechnician).copy(steps = listOf(KbStep(1, "no"))),
                entry("long").copy(steps = listOf(KbStep(1, (1..25).joinToString(" ") { "word" }))),
            ),
        )
        val p = KbRepository.problems(bad).joinToString("\n")
        listOf("kb_version", "duplicate id a", "safety line", "should be number 1", "call_technician", "25 words")
            .forEach { assertTrue("missing '$it' in:\n$p", p.contains(it)) }
    }

    @Test fun `codes normalize the letter O to zero`() {
        assertEquals("0E", Codes.normalize("OE"))
        assertEquals("0E", Codes.normalize("o-e"))
        assertEquals("E4", Codes.normalize(" e 4 "))
    }

    private fun found(m: Match) = (m as Match.Found).entry.id

    @Test fun `stage 1 exact code, ASR-garbled forms too`() {
        val r = Retriever(washer)
        assertEquals("lg_oe", found(r.find("my washer shows OE", brand = "LG")))
        assertEquals("lg_oe", found(r.find("it says oh e on the display", brand = "LG")))
        assertEquals("lg_oe", found(r.find("error O E", brand = "LG")))
        assertEquals("lg_ie", found(r.find("IE is blinking")))
        assertEquals("sam_4c", found(r.find("showing 4E")))
        assertEquals(1, (r.find("IE is blinking") as Match.Found).stage)
    }

    @Test fun `a code on two brands needs the brand`() {
        val r = Retriever(washer)
        assertEquals("sam_oe_clash", found(r.find("OE", brand = "Samsung")))
        // No brand known: stage 1 can't choose, stage 2 finds nothing clear.
        assertEquals(Match.None, r.find("OE"))
    }

    @Test fun `stage 2 keywords with a clear winner`() {
        val r = Retriever(bundled)
        val oil = r.find("how do I check the engine oil?")
        assertEquals("car_check_engine_oil", found(oil))
        assertEquals(2, (oil as Match.Found).stage)
        assertEquals("car_check_coolant", found(r.find("where do I add coolant")))
        assertEquals("car_top_up_washer_fluid", found(r.find("I need to fill the wiper fluid")))
        assertEquals("car_electrical_wiring", found(r.find("can you help me fix the wiring")))
        assertEquals("laptop_open_bottom_cover", found(r.find("how do I open this laptop to clean it")))
    }

    @Test fun `no match refuses rather than guessing`() {
        val r = Retriever(bundled)
        assertEquals(Match.None, r.find("what's the weather like"))
        assertEquals(Match.None, r.find("my fridge is making a noise"))
    }

    @Test fun `appliance narrows the search`() {
        val r = Retriever(bundled)
        assertEquals(Match.None, r.find("how do I check the engine oil", appliance = "laptop"))
    }
}
