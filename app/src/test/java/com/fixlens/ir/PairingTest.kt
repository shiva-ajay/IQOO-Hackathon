package com.fixlens.ir

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingTest {
    private fun c(model: Int, t1: String?, t2: String?) = Pairing.Candidate(model, t1, t2)

    @Test
    fun `models sharing a first code are tried once`() {
        val p = Pairing(listOf(c(0, "A", "x"), c(1, "A", "y"), c(2, "B", "z"), c(3, "A", "x")))
        assertEquals(2, p.tries)
        assertEquals(Pairing.Step.Test(1, 0, 1, 2), p.current())
        assertEquals(Pairing.Step.Test(1, 2, 2, 2), p.noResponse())
    }

    @Test
    fun `second test picks the model inside the group`() {
        val p = Pairing(listOf(c(0, "A", "x"), c(1, "A", "y"), c(2, "A", "x")))
        assertEquals(Pairing.Step.Test(2, 0, 1, 1), p.responded())
        assertEquals(Pairing.Step.Test(2, 1, 1, 1), p.noResponse())
        assertEquals(Pairing.Step.Paired(1, verified = true), p.responded())
    }

    @Test
    fun `first code alone pairs unverified when the model has no second button`() {
        val p = Pairing(listOf(c(0, "A", null)))
        assertEquals(Pairing.Step.Paired(0, verified = false), p.responded())
    }

    @Test
    fun `a partial match is kept while better ones are tried`() {
        val p = Pairing(listOf(c(0, "A", "x"), c(1, "B", "y")))
        p.responded() // A worked
        p.noResponse() // but its second code didn't: move on to B
        assertEquals(Pairing.Step.Test(1, 1, 2, 2), p.current())
        assertEquals(Pairing.Step.Paired(0, verified = false), p.noResponse())
    }

    @Test
    fun `nothing works means no match`() {
        val p = Pairing(listOf(c(0, "A", "x"), c(1, "B", "y")))
        p.noResponse()
        assertEquals(Pairing.Step.NoMatch, p.noResponse())
        assertEquals(Pairing.Step.NoMatch, p.responded())
    }

    @Test
    fun `models without a first code are skipped`() {
        assertEquals(Pairing.Step.NoMatch, Pairing(listOf(c(0, null, "x"))).current())
        assertEquals(Pairing.Step.NoMatch, Pairing(emptyList()).current())
    }
}
