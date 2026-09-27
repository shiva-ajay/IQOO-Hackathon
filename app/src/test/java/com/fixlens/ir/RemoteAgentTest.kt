package com.fixlens.ir

import com.fixlens.ir.RemoteAgent.Action
import com.fixlens.ir.RemoteCommands.Parsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAgentTest {
    private val tvKeys = setOf(Button.Power, Button.VolUp, Button.VolDown, Button.Mute, Button.Input, Button.Ok, Button.D7)

    private fun tv(raw: String, power: Boolean = false) = RemoteAgent.parse(raw, DeviceKind.Tv, tvKeys, power)

    @Test
    fun `presses a key the remote has`() {
        val d = tv("""{"press":"input","times":1,"say":"try the next source"}""") as Action.Press
        assertEquals(RemoteCommand.Press(Button.Input, 1), d.command)
        assertEquals("try the next source", d.say)
        assertEquals(RemoteCommand.Press(Button.VolUp, 3), (tv("""{"press":"volume_up","times":3}""") as Action.Press).command)
        assertEquals(RemoteCommand.Press(Button.D7, 1), (tv("""{"press":"7","times":4}""") as Action.Press).command)
    }

    @Test
    fun `tolerates fences and chatter around the json`() {
        val d = tv("Sure!\n```json\n{\"press\": \"mute\"}\n```")
        assertEquals(RemoteCommand.Press(Button.Mute, 1), (d as Action.Press).command)
    }

    @Test
    fun `refuses keys it doesn't have, and power unless asked`() {
        assertTrue(tv("""{"press":"netflix"}""") is RemoteAgent.Rejected)
        assertTrue(tv("""{"press":"ch_up"}""") is RemoteAgent.Rejected) // not on this remote
        assertTrue(tv("""{"press":"power"}""") is RemoteAgent.Rejected)
        assertTrue(tv("""{"press":"power"}""", power = true) is Action.Press)
        assertTrue(tv("no idea") is RemoteAgent.Rejected)
        assertTrue(RemoteAgent.goalAllowsPower("turn the TV off"))
        assertFalse(RemoteAgent.goalAllowsPower("there's no sound"))
    }

    @Test
    fun `wait ask and done`() {
        assertEquals(Action.Wait(4, "let it load"), tv("""{"wait":9,"say":"let it load"}"""))
        assertEquals(Action.Ask("Can you hear it now?"), tv("""{"ask":"Can you hear it now?"}"""))
        assertEquals(Action.Done("The picture is back."), tv("""{"done":"The picture is back."}"""))
    }

    @Test
    fun `ac actions`() {
        fun ac(raw: String) = RemoteAgent.parse(raw, DeviceKind.Ac, emptySet(), goalAllowsPower = false)
        assertEquals(RemoteCommand.Ac(AcChange.Temp(24)), (ac("""{"press":"temp","value":24}""") as Action.Press).command)
        assertEquals(RemoteCommand.Ac(AcChange.SetMode(AcMode.Cool)), (ac("""{"press":"mode","value":"cool"}""") as Action.Press).command)
        assertEquals(RemoteCommand.Ac(AcChange.SetFan(FanSpeed.High)), (ac("""{"press":"fan","value":"high"}""") as Action.Press).command)
        assertTrue(ac("""{"press":"temp","value":40}""") is RemoteAgent.Rejected)
        assertTrue(ac("""{"press":"off"}""") is RemoteAgent.Rejected)
    }

    @Test
    fun `prompt names the goal, the keys and what happened`() {
        val p = RemoteAgent.prompt("LG TV", DeviceKind.Tv, "switch to HDMI 2", listOf("input", "ok"), listOf("pressed input"), 2, null)
        assertTrue(p.contains("switch to HDMI 2"))
        assertTrue(p.contains("Keys you can press: input, ok"))
        assertTrue(p.contains("1. pressed input"))
        assertTrue(p.contains("step 2 of at most"))
    }

    @Test
    fun `open-ended goals go to the agent, plain commands stay fast`() {
        assertEquals(Parsed.Agent("switch to HDMI 2"), RemoteCommands.parse("switch to HDMI 2", DeviceKind.Tv))
        assertEquals(Parsed.Agent("find the input with the laptop"), RemoteCommands.parse("find the input with the laptop", DeviceKind.Tv))
        assertEquals(Parsed.Command(RemoteCommand.Press(Button.VolUp)), RemoteCommands.parse("volume up", DeviceKind.Tv))
        assertEquals(
            Parsed.Command(RemoteCommand.Keys(listOf(Button.D1, Button.D0, Button.D5))),
            RemoteCommands.parse("go to channel 105", DeviceKind.Tv),
        )
        val take = RemoteCommands.parse("take the remote and find the laptop input", null) as Parsed.Take
        assertEquals("take the remote and find the laptop input", take.goal)
    }

    @Test
    fun `pairing can step back for a late answer`() {
        val p = Pairing(listOf(Pairing.Candidate(0, "A", "x"), Pairing.Candidate(1, "B", "y")))
        val mark = p.mark()
        p.noResponse() // moved on to B
        p.restore(mark) // "it responded" was about A
        assertEquals(Pairing.Step.Test(2, 0, 1, 2), p.responded())
    }
}

class ScreenProbeTest {
    private val n = ScreenProbe.GRID * ScreenProbe.GRID

    private fun frame(base: Float = 80f, jitter: Float = 1f, seed: Int = 0, overlay: IntRange? = null, shift: Float = 0f) =
        FloatArray(n) { i ->
            val noise = ((i * 7919 + seed * 104729) % 100) / 100f * jitter
            base + noise + shift + if (overlay != null && i in overlay) 90f else 0f
        }

    @Test
    fun `a volume bar appearing is a change`() {
        val before = List(8) { frame(seed = it) }
        val bar = 500 until 520 // a strip of cells near the bottom
        val after = List(10) { frame(seed = 20 + it, overlay = bar) }
        assertEquals(ScreenProbe.Verdict.Changed, ScreenProbe.detect(before, after))
    }

    @Test
    fun `a still screen is no change, even if exposure shifts`() {
        val before = List(8) { frame(seed = it) }
        assertEquals(ScreenProbe.Verdict.Same, ScreenProbe.detect(before, List(10) { frame(seed = 30 + it) }))
        assertEquals(ScreenProbe.Verdict.Same, ScreenProbe.detect(before, List(10) { frame(seed = 30 + it, shift = 25f) }))
    }

    @Test
    fun `a moving camera or a busy picture is unsure`() {
        val before = List(8) { frame(seed = it) }
        val moved = List(10) { i -> FloatArray(n) { j -> ((j * 31 + i * 97) % 200).toFloat() } }
        assertEquals(ScreenProbe.Verdict.Unsure, ScreenProbe.detect(before, moved))
        val video = List(8) { i -> FloatArray(n) { j -> ((j * 13 + i * 53) % 60).toFloat() } }
        assertEquals(ScreenProbe.Verdict.Unsure, ScreenProbe.detect(video, List(10) { frame(seed = it) }))
    }
}
