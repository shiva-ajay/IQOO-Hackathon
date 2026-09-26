package com.fixlens.kb

import com.fixlens.guide.MemoryRules
import com.fixlens.kb.Retriever.Match
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    private val generic = KnowledgeBase(
        "t",
        listOf(
            entry("drain", appliance = "washing_machine", brand = "generic", aliases = listOf("not draining")).copy(
                brandCodes = listOf(BrandCode("LG", listOf("OE"), listOf("oh e")), BrandCode("Samsung", listOf("5C", "5E"))),
            ),
            entry("door", appliance = "washing_machine", brand = "generic", aliases = listOf("door won't open")).copy(
                brandCodes = listOf(BrandCode("Samsung", listOf("dC")), BrandCode("LG", listOf("dE"))),
            ),
            entry("ac_cool", appliance = "air_conditioner", brand = "generic", aliases = listOf("not cooling")),
            entry("fridge_cool", appliance = "refrigerator", brand = "generic", aliases = listOf("not cooling")),
        ),
    )

    @Test fun `brand codes lead to the generic entry`() {
        val r = Retriever(generic)
        assertEquals("drain", found(r.find("it's showing 5C")))
        assertEquals("drain", found(r.find("my LG says oh e")))
        assertEquals("door", found(r.find("error dC on my samsung")))
        // A letters-only code needs a cue or a brand, so "dE" in passing isn't read as a code.
        assertEquals(Match.None, r.find("de"))
        // With the brand known, another brand's code doesn't count.
        assertEquals(Match.None, r.find("error 5C", brand = "LG"))
    }

    @Test fun `negations match either way`() {
        val r = Retriever(generic)
        assertEquals("drain", found(r.find("my washing machine won't drain", appliance = "washing_machine")))
        assertEquals("door", found(r.find("the door doesn't open", appliance = "washing_machine")))
    }

    @Test fun `the appliance decides a symptom two appliances share`() {
        val r = Retriever(generic)
        assertEquals(Match.None, r.find("it's not cooling"))
        val (appliance, _) = MemoryRules.kbContext("my fridge is not cooling", null)
        assertEquals("fridge_cool", found(r.find("my fridge is not cooling", appliance)))
        val (ac, _) = MemoryRules.kbContext("the AC isn't cooling", null)
        assertEquals("ac_cool", found(r.find("the AC isn't cooling", ac)))
    }

    @Test fun `every KB appliance can be named by the user`() {
        val unknown = bundled.entries.map { it.appliance }.toSet() - MemoryRules.KB_APPLIANCES
        assertTrue("appliances the notes never name: $unknown", unknown.isEmpty())
    }

    @Serializable
    private data class Query(val q: String, val expect: String, val from: String = "")

    /** tools/kb/test_queries.json, written with the entries: each question, looked up the way the app does. */
    @Test fun `the test queries find their entries`() {
        val queries = Json.decodeFromString<List<Query>>(File("../tools/kb/test_queries.json").readText())
        val r = Retriever(bundled)
        val wrong = queries.mapNotNull { q ->
            val (appliance, brand) = MemoryRules.kbContext(q.q, null)
            val got = (r.find(q.q, appliance, brand) as? Match.Found)?.entry?.id ?: "NONE"
            if (got == q.expect) null else "[${q.from}] \"${q.q}\" -> $got, expected ${q.expect} (appliance $appliance)"
        }
        assertTrue("${wrong.size} of ${queries.size} wrong:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun `no match refuses rather than guessing`() {
        val r = Retriever(bundled)
        assertEquals(Match.None, r.find("what's the weather like"))
        assertEquals(Match.None, r.find("my printer has a paper jam", MemoryRules.kbContext("my printer has a paper jam", null).first))
    }

    @Test fun `appliance narrows the search`() {
        val r = Retriever(bundled)
        assertEquals(Match.None, r.find("how do I check the engine oil", appliance = "laptop"))
    }
}
