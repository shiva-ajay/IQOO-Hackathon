package com.fixlens.voice

import com.fixlens.voice.SpeechChunker.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunkerTest {

    /** Feeds [chunks] in order; returns the pieces, and how many chunks had been fed when each came out. */
    private fun run(chunks: List<String>): Pair<List<Piece>, List<Int>> {
        val chunker = SpeechChunker()
        val pieces = mutableListOf<Piece>()
        val at = mutableListOf<Int>()
        chunks.forEachIndexed { i, c -> chunker.feed(c).forEach { pieces += it; at += i + 1 } }
        chunker.flush()?.let { pieces += it; at += chunks.size + 1 }
        return pieces to at
    }

    private fun texts(chunks: List<String>) = run(chunks).first.map { it.text }
    private fun charByChar(s: String) = s.map { it.toString() }

    @Test fun `two sentences stream out one by one`() {
        val (pieces, at) = run(listOf("Pull the dipstick ", "out slowly. ", "Wipe it clean."))
        assertEquals(listOf("Pull the dipstick out slowly.", "Wipe it clean."), pieces.map { it.text })
        assertEquals(2, at[0]) // cut as soon as the space after the full stop arrived
        assertTrue(pieces.all { it.sentenceEnd })
    }

    @Test fun `split across single characters`() {
        assertEquals(
            listOf("That's the yellow handle.", "Pull it out!", "Ready?"),
            texts(charByChar("That's the yellow handle. Pull it out! Ready?")),
        )
    }

    @Test fun `a full stop at the end of a chunk waits for the next one`() {
        val chunker = SpeechChunker()
        assertEquals(emptyList<Piece>(), chunker.feed("Pour 2."))
        assertEquals(emptyList<Piece>(), chunker.feed("5 litres of oil."))
        assertEquals(Piece("Pour 2.5 litres of oil.", true), chunker.flush())
    }

    @Test fun `abbreviations don't end a sentence, and later pieces aren't cut at commas`() {
        assertEquals(
            listOf("Okay.", "Wipe it with a cloth, e.g. an old shirt, then check again."),
            texts(listOf("Okay. Wipe it with a cloth, e.g. an old shirt, then check again.")),
        )
    }

    @Test fun `an ellipsis ends a phrase`() {
        assertEquals(listOf("Hmm...", "that's the coolant tank."), texts(listOf("Hmm... that's the coolant tank.")))
    }

    @Test fun `the first piece ends at a comma once it has four words`() {
        val (pieces, _) = run(listOf("That's the dipstick handle, pull it out, then wipe it clean."))
        assertEquals(
            listOf(Piece("That's the dipstick handle,", false), Piece("pull it out, then wipe it clean.", true)),
            pieces,
        )
    }

    @Test fun `a short opener before a comma isn't cut`() {
        assertEquals(listOf("Okay, open the bonnet.", "Then look."), texts(listOf("Okay, open the bonnet. Then look.")))
    }

    @Test fun `semicolons and colons end a clause`() {
        val (pieces, _) = run(listOf("Two things: the cap; then the dipstick."))
        assertEquals(listOf("Two things:", "the cap;", "then the dipstick."), pieces.map { it.text })
        assertEquals(listOf(false, false, true), pieces.map { it.sentenceEnd })
    }

    @Test fun `a time isn't cut at its colon`() {
        assertEquals(listOf("Wait until 10:30 tonight."), texts(listOf("Wait until 10:30 tonight.")))
    }

    @Test fun `newlines and list marks`() {
        assertEquals(
            listOf("Do this:", "Open the cap", "Check the level."),
            texts(listOf("Do this:\n1. Open the cap\n2. Check the level.")),
        )
    }

    @Test fun `a long run-on is cut at its last comma, then at a space`() {
        val longClause = "and then " + "slowly ".repeat(12) + "turn it, and keep going " + "on ".repeat(60)
        val pieces = texts(listOf("It's fine. $longClause"))
        assertEquals("It's fine.", pieces.first())
        assertTrue(pieces[1].endsWith("turn it,"))
        assertTrue(pieces.size >= 4)
        assertTrue(pieces.all { it.length <= SpeechChunker.HARD_MAX })
        assertEquals(("It's fine. $longClause").split(Regex("\\s+")).filter { it.isNotEmpty() }, pieces.joinToString(" ").split(" "))
    }

    @Test fun `markdown and emoji are not read out`() {
        assertEquals(
            listOf("The oil cap is here.", "Turn it slowly."),
            texts(listOf("**The oil cap** is here 🔧. `Turn` it _slowly_ ✓.")),
        )
    }

    @Test fun `symbols become words`() {
        assertEquals(listOf("Oil and coolant, about 20 percent at 90 degrees Celsius."), texts(listOf("Oil & coolant, about 20% at 90°C.")))
    }

    @Test fun `punctuation-only pieces are dropped`() {
        assertEquals(listOf("Done."), texts(listOf("Done. ... ** \n")))
    }

    @Test fun `flush returns the unfinished rest`() {
        val chunker = SpeechChunker()
        assertEquals(emptyList<Piece>(), chunker.feed("Wipe it clean"))
        assertEquals(Piece("Wipe it clean", true), chunker.flush())
        assertEquals(null, chunker.flush())
    }

    @Test fun `split cuts a KB line the same way`() {
        val say = "Let's check the coolant level in the see-through tank. Only check the coolant when the engine is completely cold."
        assertEquals(
            listOf("Let's check the coolant level in the see-through tank.", "Only check the coolant when the engine is completely cold."),
            SpeechChunker.split(say).map { it.text },
        )
    }
}
