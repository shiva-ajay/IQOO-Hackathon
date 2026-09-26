package com.fixlens.vision

import com.fixlens.vision.GroundingParser.Grounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingParserTest {

    private class Run(chunks: List<String>, finish: Boolean = true) {
        val groundings = mutableListOf<Grounding>()
        val texts = mutableListOf<String>()
        val streamed = mutableListOf<ModelBox>()
        /** How many text chunks had been emitted when the grounding fired (-1 if it never did). */
        var textChunksBeforeGrounding = -1
        val parser = GroundingParser(
            onGrounding = { groundings += it; textChunksBeforeGrounding = texts.size },
            onText = { texts += it },
            onTarget = { streamed += it },
        )

        init {
            chunks.forEach(parser::feed)
            if (finish) parser.finish()
        }

        val text get() = texts.joinToString("")
        val boxes get() = (groundings.single() as Grounding.Targets).boxes
        val box get() = boxes.single()
    }

    private fun charByChar(s: String) = s.map { it.toString() }

    @Test fun `object box line then text`() {
        val r = Run(listOf("{\"bbox_2d\":[120,340,560,780],\"label\":\"drain filter\"}\nOpen the small flap at the bottom."))
        assertEquals(ModelBox(120f, 340f, 560f, 780f, "drain filter"), r.box)
        assertEquals("Open the small flap at the bottom.", r.text)
        assertEquals("Open the small flap at the bottom.", r.parser.text)
    }

    @Test fun `box fires before the text finishes`() {
        val r = Run(listOf("{\"bbox_2d\":[1,2,3,4],", "\"label\":\"cap\"}", "\nThat's ", "the cap."), finish = false)
        assertEquals(1, r.groundings.size)
        assertEquals(0, r.textChunksBeforeGrounding)
        assertEquals("That's the cap.", r.text)
    }

    @Test fun `split across single characters`() {
        val reply = "  {\"bbox_2d\": [10, 20, 300, 400], \"label\": \"dipstick\"}\n\nPull it out slowly."
        val r = Run(charByChar(reply))
        assertEquals(ModelBox(10f, 20f, 300f, 400f, "dipstick"), r.box)
        assertEquals("Pull it out slowly.", r.text)
    }

    @Test fun `fenced native array over several lines`() {
        val reply = "```json\n[\n\t{\"bbox_2d\": [233, 318, 552, 641], \"label\": \"program dial\"}\n]\n```\nTurn the dial to OFF first."
        val r = Run(charByChar(reply))
        assertEquals(ModelBox(233f, 318f, 552f, 641f, "program dial"), r.box)
        assertEquals("Turn the dial to OFF first.", r.text)
    }

    @Test fun `bare array without fence`() {
        val r = Run(listOf("[{\"bbox_2d\":[5,6,7,8],\"label\":\"x\"}]\nHere."))
        assertEquals(ModelBox(5f, 6f, 7f, 8f, "x"), r.box)
        assertEquals("Here.", r.text)
    }

    @Test fun `null box`() {
        val r = Run(listOf("{\"bbox_2d\":null}\nI can't see a part to point at."))
        assertEquals(listOf<Grounding>(Grounding.NoBox), r.groundings)
        assertEquals("I can't see a part to point at.", r.text)
    }

    @Test fun `empty array is no box`() {
        val r = Run(listOf("[]\nNothing to point at."))
        assertEquals(listOf<Grounding>(Grounding.NoBox), r.groundings)
        assertEquals("Nothing to point at.", r.text)
    }

    @Test fun `garbage first line is all text`() {
        val reply = "The display shows E4, which means a drain problem.\nCheck the filter."
        val r = Run(charByChar(reply))
        assertEquals(listOf<Grounding>(Grounding.NotJson), r.groundings)
        assertEquals(reply, r.text)
    }

    @Test fun `truncated json is dropped not shown`() {
        val r = Run(listOf("{\"bbox_2d\":[12,34,"))
        assertEquals(listOf<Grounding>(Grounding.Invalid), r.groundings)
        assertEquals("", r.text)
    }

    @Test fun `unreadable json is dropped and the text kept`() {
        val r = Run(listOf("{\"bbox_2d\":[1,2,3]}\nThe cap is on the left."))
        assertEquals(listOf<Grounding>(Grounding.Invalid), r.groundings)
        assertEquals("The cap is on the left.", r.text)
    }

    @Test fun `point answer becomes a point box`() {
        val r = Run(listOf("{\"point_2d\":[500,250],\"label\":\"button\"}\nPress it."))
        assertEquals(ModelBox(500f, 250f, 500f, 250f, "button", isPoint = true), r.box)
    }

    @Test fun `braces inside the label do not end the json early`() {
        val r = Run(listOf("{\"bbox_2d\":[1,2,30,40],\"label\":\"cap } [x]\"}\nok"))
        assertEquals("cap } [x]", r.box.label)
        assertEquals("ok", r.text)
    }

    @Test fun `text on the same line after the json`() {
        val r = Run(listOf("{\"bbox_2d\":[1,2,30,40]} That's the cap."))
        assertEquals("That's the cap.", r.text)
        assertNull(r.box.label)
    }

    @Test fun `empty reply resolves on finish`() {
        val r = Run(listOf("", "  \n"))
        assertEquals(listOf<Grounding>(Grounding.NotJson), r.groundings)
        assertEquals("", r.text)
    }

    @Test fun `a lone backtick waits for the fence`() {
        val r = Run(listOf("`"), finish = false)
        assertTrue(r.groundings.isEmpty())
        r.parser.feed("``json\n{\"bbox_2d\":[1,2,30,40]}\n```\nHi")
        r.parser.finish() // an answer this short is held back until the stream ends
        assertEquals(ModelBox(1f, 2f, 30f, 40f), r.box)
        assertEquals("Hi", r.text)
    }

    @Test fun `grounding fires exactly once`() {
        val r = Run(listOf("{\"bbox_2d\":[1,2,30,40]}\n", "{\"bbox_2d\":[5,5,9,9]}"))
        assertEquals(1, r.groundings.size)
        assertFalse(r.text.isEmpty()) // a second JSON is just text
    }

    @Test fun `a Fixy speaker tag is stripped even when split`() {
        val r = Run(listOf("{\"bbox_2d\":[1,2,30,40]}\nFi", "xy: ", "The cap", " is here."))
        assertEquals("The cap is here.", r.text)
        assertEquals("The cap is here.", r.parser.text)
    }

    @Test fun `a short answer is flushed on finish`() {
        val r = Run(listOf("{\"bbox_2d\":null}\nOk."))
        assertEquals("Ok.", r.text)
    }

    @Test fun `words starting with fix are kept`() {
        val r = Run(listOf("{\"bbox_2d\":null}\nFixing it is easy."))
        assertEquals("Fixing it is easy.", r.text)
    }

    @Test fun `list parts stream out before the list closes`() {
        val r = Run(
            listOf(
                "[{\"point_2d\":[100,200],\"label\":\"screw\"},",
                " {\"point_2d\":[300,",
                "400],\"label\":\"screw\"}",
            ),
            finish = false,
        )
        assertEquals(2, r.streamed.size)
        assertTrue(r.groundings.isEmpty()) // the list isn't closed yet
        r.parser.feed(", {\"bbox_2d\":[10,10,50,50],\"label\":\"cover\"}]\nUndo these screws first.")
        r.parser.finish()
        assertEquals(3, r.boxes.size)
        assertEquals(ModelBox(100f, 200f, 100f, 200f, "screw", isPoint = true), r.boxes[0])
        assertEquals(ModelBox(10f, 10f, 50f, 50f, "cover"), r.boxes[2])
        assertEquals(r.boxes, r.streamed)
        assertEquals("Undo these screws first.", r.text)
    }

    @Test fun `a list cut off by the token limit keeps its complete parts`() {
        val r = Run(listOf("[{\"point_2d\":[1,2]},{\"point_2d\":[3,4]},{\"point_2d\":[5,"))
        assertEquals(2, r.boxes.size)
        assertEquals("", r.text)
    }

    @Test fun `a fenced list whose closing fence arrives later never shows in the text`() {
        val reply = "```json\n[\n  {\"point_2d\": [512, 300], \"label\": \"screw\"}\n]\n``" + "`\nRemove the screw."
        val r = Run(charByChar(reply))
        assertEquals(1, r.boxes.size)
        assertEquals("Remove the screw.", r.text)
    }

    @Test fun `unreadable list entries are skipped`() {
        val r = Run(listOf("[{\"foo\":1},{\"bbox_2d\":[1,2,30,40]}]\nHere."))
        assertEquals(listOf(ModelBox(1f, 2f, 30f, 40f)), r.boxes)
        val bad = Run(listOf("[{\"foo\":1}]\nHere."))
        assertEquals(listOf<Grounding>(Grounding.Invalid), bad.groundings)
        assertEquals("Here.", bad.text)
    }

    @Test fun `runaway lists are capped`() {
        val many = (1..40).joinToString(",", "[", "]") { "{\"point_2d\":[$it,$it]}" }
        val r = Run(listOf(many + "\nok"))
        assertEquals(GroundingParser.MAX_TARGETS, r.boxes.size)
    }

    @Test fun `a bare box array still works`() {
        val r = Run(listOf("[10, 20, 300, 400]\nThere."))
        assertEquals(ModelBox(10f, 20f, 300f, 400f), r.box)
    }

    @Test fun `an unbracketed list is read as a list`() {
        val r = Run(charByChar("{\"point_2d\":[206,445],\"label\":\"screw\"},{\"point_2d\":[300,635],\"label\":\"screw\"}]\nThose are the screws."))
        assertEquals(2, r.boxes.size)
        assertEquals(2, r.streamed.size)
        assertEquals("Those are the screws.", r.text)
    }

    @Test fun `an unbracketed list without the closing bracket ends at the text`() {
        val r = Run(listOf("{\"point_2d\":[1,2]},\n{\"point_2d\":[3,4]}\nUnscrew them."))
        assertEquals(2, r.boxes.size)
        assertEquals("Unscrew them.", r.text)
    }

    @Test fun `the first part streams before we know whether a list follows`() {
        val r = Run(listOf("{\"point_2d\":[1,2]}"), finish = false)
        assertEquals(1, r.streamed.size)
        assertTrue(r.groundings.isEmpty())
    }

    @Test fun `a wrapper object with a points list`() {
        val r = Run(listOf("{\"points\": [{\"point_2d\": [270, 630], \"label\": \"screw\"}, {\"point_2d\": [670, 630], \"label\": \"screw\"}]}\n"))
        assertEquals(2, r.boxes.size)
        assertEquals("screw", r.boxes[1].label)
    }
}
