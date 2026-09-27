package com.fixlens.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words of the "is it the TV or your remote?" test. */
class RemoteTestFlowTest {
    @Test
    fun `still broken during a remote guide`() {
        for (s in listOf("it's still not working", "I changed the batteries, still nothing works", "didn't help",
            "I already put new batteries", "can you diagnose the issue", "figure out what's wrong")) {
            assertTrue(s, RemoteCommands.stillBroken(s))
        }
        assertFalse(RemoteCommands.stillBroken("okay done"))
        assertFalse(RemoteCommands.stillBroken("thanks, it works now"))
    }

    @Test
    fun `asking for the test outright`() {
        assertTrue(RemoteCommands.wantsRemoteTest("test the TV with your remote"))
        assertTrue(RemoteCommands.wantsRemoteTest("is it the remote or the TV?"))
        assertTrue(RemoteCommands.wantsRemoteTest("use your remote"))
        // "Diagnose" alone only counts inside a remote guide, never for any repair.
        assertFalse(RemoteCommands.wantsRemoteTest("diagnose the washing machine"))
    }

    @Test
    fun `the user's remote doesn't work`() {
        for (s in listOf(
            "my TV remote is not working",
            "if I try to mute with the physical remote it is not working",
            "my TV was not working when I pressed mute on the remote",
            "the remote does nothing",
            "the AC remote stopped working",
            "tv not responding to the remote",
        )) assertTrue(s, RemoteCommands.remoteBroken(s))
        assertFalse(RemoteCommands.remoteBroken("where is the remote"))
        assertFalse(RemoteCommands.remoteBroken("your remote isn't working")) // Fixy's remote: pairing, not this test
        assertFalse(RemoteCommands.remoteBroken("the TV is not working"))
    }

    @Test
    fun `the test presses the key the user said doesn't work`() {
        assertEquals(Button.Mute, RemoteCommands.brokenKey("if I try to mute with the physical remote it is not working"))
        assertEquals(Button.VolUp, RemoteCommands.brokenKey("the volume button on my remote does nothing"))
        assertEquals(Button.VolDown, RemoteCommands.brokenKey("volume down isn't working"))
        assertEquals(Button.ChUp, RemoteCommands.brokenKey("I can't change channels with the remote"))
        assertEquals(Button.Power, RemoteCommands.brokenKey("the remote won't turn the TV on"))
        assertEquals(Button.Input, RemoteCommands.brokenKey("the source button doesn't work"))
        assertNull(RemoteCommands.brokenKey("my TV remote is not working"))
    }

    @Test
    fun `same or a different tv`() {
        assertEquals(true, RemoteCommands.sameDevice("same TV"))
        assertEquals(true, RemoteCommands.sameDevice("yes, the same one"))
        assertEquals(false, RemoteCommands.sameDevice("it's a new TV"))
        assertEquals(false, RemoteCommands.sameDevice("no, a different one"))
        assertNull(RemoteCommands.sameDevice("hmm"))
    }

    @Test
    fun `phone is aimed`() {
        for (s in listOf("ready", "yeah it's pointed", "I'm pointing at it", "go ahead", "okay")) assertTrue(s, RemoteCommands.ready(s))
        assertFalse(RemoteCommands.ready("wait a second"))
    }
}
