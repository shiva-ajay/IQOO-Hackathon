package com.fixlens.ir

import com.fixlens.ir.RemoteCommands.Answer
import com.fixlens.ir.RemoteCommands.Parsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCommandsTest {
    private fun ac(text: String) = (RemoteCommands.parse(text, DeviceKind.Ac) as? Parsed.Command)?.command
    private fun tv(text: String) = (RemoteCommands.parse(text, DeviceKind.Tv) as? Parsed.Command)?.command

    @Test
    fun `taking the remote`() {
        assertEquals(Parsed.Take(null), RemoteCommands.parse("take the remote", null))
        assertEquals(Parsed.Take(DeviceKind.Tv), RemoteCommands.parse("Can you control the TV?", null))
        assertEquals(Parsed.Take(DeviceKind.Ac), RemoteCommands.parse("pair with the AC", null))
        assertEquals(Parsed.Take(DeviceKind.Tv), RemoteCommands.parse("control the Samsung TV", null))
        assertEquals(Parsed.Take(DeviceKind.Ac), RemoteCommands.parse("control my Blue Star AC", null))
        assertEquals(Parsed.Take(DeviceKind.Ac), RemoteCommands.parse("take remote control access", DeviceKind.Ac))
    }

    @Test
    fun `a command for a device not held yet takes its remote first`() {
        assertEquals(Parsed.Take(DeviceKind.Ac, RemoteCommand.Ac(AcChange.On)), RemoteCommands.parse("turn on the AC", null))
        assertEquals(
            Parsed.Take(DeviceKind.Tv, null, RemoteRoutine.FindPicture),
            RemoteCommands.parse("take the remote and check the TV", null),
        )
    }

    @Test
    fun `questions are left for the guide`() {
        assertNull(RemoteCommands.parse("how do I turn off the TV", null))
        assertNull(RemoteCommands.parse("why won't the AC turn on", DeviceKind.Ac))
        assertNull(RemoteCommands.parse("where is the dipstick", null))
    }

    @Test
    fun `describing a problem is not a command`() {
        assertNull(RemoteCommands.parse("it's noisy when I turn on the AC", null))
        assertNull(RemoteCommands.parse("it makes a clicking sound when I turn it off", DeviceKind.Ac))
        assertEquals(Parsed.Take(DeviceKind.Ac, RemoteCommand.Ac(AcChange.On)), RemoteCommands.parse("can you please turn on the AC", null))
    }

    @Test
    fun `guide words are not remote commands`() {
        for (word in listOf("done", "next", "back", "repeat", "stop")) assertNull(word, RemoteCommands.parse(word, DeviceKind.Tv))
    }

    @Test
    fun `releasing`() {
        assertEquals(Parsed.Release, RemoteCommands.parse("stop controlling the TV", DeviceKind.Tv))
        assertNull(RemoteCommands.parse("stop controlling the TV", null))
    }

    @Test
    fun `ac temperatures`() {
        assertEquals(RemoteCommand.Ac(AcChange.Temp(24)), ac("set it to 24"))
        assertEquals(RemoteCommand.Ac(AcChange.Temp(22)), ac("make it 22 degrees"))
        assertEquals(RemoteCommand.Ac(AcChange.Temp(25)), ac("twenty five degrees please"))
        assertNull(ac("set it to 45"))
        assertEquals(RemoteCommand.Ac(AcChange.Warmer), ac("it's too cold, make it warmer"))
        assertEquals(RemoteCommand.Ac(AcChange.Cooler), ac("lower the temperature"))
    }

    @Test
    fun `ac power modes and fan`() {
        assertEquals(RemoteCommand.Ac(AcChange.Off), ac("turn it off"))
        assertEquals(RemoteCommand.Ac(AcChange.On), ac("switch the AC on"))
        assertEquals(RemoteCommand.Ac(AcChange.SetMode(AcMode.Dry)), ac("put it in dry mode"))
        assertEquals(RemoteCommand.Ac(AcChange.SetMode(AcMode.Cool)), ac("turn it on in cool mode"))
        assertEquals(RemoteCommand.Ac(AcChange.SetFan(FanSpeed.High)), ac("fan speed to high"))
        assertEquals(RemoteCommand.Ac(AcChange.SetFan(FanSpeed.Low)), ac("low fan"))
        assertEquals(RemoteCommand.Ac(AcChange.Swing), ac("turn on the swing"))
    }

    @Test
    fun `tv buttons`() {
        assertEquals(RemoteCommand.Press(Button.VolUp, 3), tv("volume up by 3"))
        assertEquals(RemoteCommand.Press(Button.VolDown), tv("make it quieter"))
        assertEquals(RemoteCommand.Press(Button.Mute), tv("unmute"))
        assertEquals(RemoteCommand.Press(Button.Input), tv("change the input"))
        assertEquals(RemoteCommand.Press(Button.Power), tv("turn the TV off"))
        assertEquals(RemoteCommand.Press(Button.ChUp), tv("next channel"))
        assertEquals(RemoteCommand.Press(Button.Ok), tv("press ok"))
        assertNull(tv("what's on the screen"))
    }

    @Test
    fun `routines`() {
        assertEquals(Parsed.Routine(RemoteRoutine.AcCoolDown), RemoteCommands.parse("the AC is not cooling", DeviceKind.Ac))
        assertEquals(Parsed.Routine(RemoteRoutine.FindPicture), RemoteCommands.parse("the TV shows no signal", DeviceKind.Tv))
        assertEquals(Parsed.Routine(RemoteRoutine.BringSoundBack), RemoteCommands.parse("I can't hear anything", DeviceKind.Tv))
        assertEquals(Parsed.Routine(RemoteRoutine.FindPicture), RemoteCommands.parse("diagnose the TV", DeviceKind.Tv))
        // Not holding the remote: a repair question like any other.
        assertNull(RemoteCommands.parse("the AC is not cooling", null))
    }

    @Test
    fun `pairing answers`() {
        assertEquals(Answer.Yes, RemoteCommands.answer("yes"))
        assertEquals(Answer.Yes, RemoteCommands.answer("yeah it beeped"))
        assertEquals(Answer.Yes, RemoteCommands.answer("it worked"))
        assertEquals(Answer.No, RemoteCommands.answer("no"))
        assertEquals(Answer.No, RemoteCommands.answer("nothing happened"))
        assertEquals(Answer.No, RemoteCommands.answer("it didn't work"))
        assertEquals(Answer.No, RemoteCommands.answer("next"))
        assertNull(RemoteCommands.answer("hmm"))
    }

    @Test
    fun `fan words don't steal ac fan speed`() {
        assertEquals(DeviceKind.Ac, RemoteCommands.deviceIn("set the AC fan speed to high"))
        assertEquals(DeviceKind.Fan, RemoteCommands.deviceIn("turn on the ceiling fan"))
    }
}
