package com.fixlens.ir

import com.fixlens.guide.FixyPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class RemoteLogicTest {
    private val brands = listOf(
        BrandMatcher.Candidate("LG", listOf("l g")),
        BrandMatcher.Candidate("Samsung", listOf("samsang")),
        BrandMatcher.Candidate("Blue Star", listOf("bluestar")),
        BrandMatcher.Candidate("Star"),
        BrandMatcher.Candidate("Voltas", listOf("walters")),
        BrandMatcher.Candidate("Mi", listOf("xiaomi")),
        BrandMatcher.Candidate("Best"),
    )

    @Test
    fun `brand named in a sentence`() {
        assertEquals("LG", BrandMatcher.match("it's an LG", brands).best)
        assertTrue(BrandMatcher.match("it's an LG", brands).exact)
        assertEquals("Blue Star", BrandMatcher.match("the blue star AC", brands).best)
        assertEquals("Voltas", BrandMatcher.match("walters", brands).best)
        assertEquals("Mi", BrandMatcher.match("my Mi TV", brands).best)
    }

    @Test
    fun `brand near misses are guesses`() {
        val r = BrandMatcher.match("samsong", brands)
        assertFalse(r.exact)
        assertEquals("Samsung", r.best)
    }

    @Test
    fun `everyday words are not brands`() {
        assertNull(BrandMatcher.match("this is the best one", brands).best)
        assertFalse(BrandMatcher.match("can you help me", brands).exact)
    }

    @Test
    fun `ac changes stay in range and switch it on`() {
        val caps = AcCaps.ANY
        val off = AcState(power = false, tempC = 30)
        val (warmer, key) = AcChange.apply(off, AcChange.Warmer, caps)
        assertEquals(30, warmer.tempC)
        assertTrue(warmer.power)
        assertEquals(AcKey.TempUp, key)
        assertEquals(16, AcChange.apply(AcState(tempC = 16), AcChange.Cooler, caps).first.tempC)
        assertEquals(FanSpeed.Low, AcChange.apply(AcState(fan = FanSpeed.Auto), AcChange.SetFan(null), caps).first.fan)
        assertEquals(FanSpeed.Auto, AcChange.apply(AcState(fan = FanSpeed.High), AcChange.SetFan(null), caps).first.fan)
    }

    @Test
    fun `ac state is clamped to what the model accepts`() {
        val caps = AcCaps(
            modes = setOf(AcMode.Cool, AcMode.Dry),
            temps = mapOf(AcMode.Cool to 18..28, AcMode.Dry to null),
            fans = mapOf(AcMode.Cool to setOf(FanSpeed.Low, FanSpeed.High)),
            swing = mapOf(AcMode.Cool to false),
        )
        val s = caps.clamp(AcState(mode = AcMode.Heat, tempC = 30, fan = FanSpeed.Auto, swing = true))
        assertEquals(AcMode.Cool, s.mode)
        assertEquals(28, s.tempC)
        assertEquals(FanSpeed.Low, s.fan)
        assertFalse(s.swing)
    }

    @Test
    fun `ir patterns are made sendable or refused`() {
        assertEquals(3, IrPattern(38000, intArrayOf(9000, 4500, 560, 560)).validOrNull()!!.timings.size)
        assertNull(IrPattern(38000, intArrayOf(9000, 0, 560)).validOrNull())
        assertNull(IrPattern(38000, IntArray(5) { 500_000 }).validOrNull())
    }

    @Test
    fun `beep is a short loud tone, speech-like noise is not`() {
        val rnd = Random(7)
        val room = FloatArray(8000) { (rnd.nextFloat() - 0.5f) * 0.01f }
        fun tone(hz: Double, ms: Int, amp: Float) = FloatArray(16 * ms) { (amp * sin(2 * PI * hz * it / 16000)).toFloat() }
        val quiet = FloatArray(4000) { (rnd.nextFloat() - 0.5f) * 0.01f }
        assertTrue(BeepProbe.detect(room, quiet + tone(2700.0, 150, 0.2f) + quiet))
        assertFalse(BeepProbe.detect(room, quiet + quiet + quiet))
        // Loud broadband noise (a door, a voice) isn't tonal.
        assertFalse(BeepProbe.detect(room, FloatArray(16000) { (rnd.nextFloat() - 0.5f) * 0.6f }))
        // A single 20 ms click is too short.
        assertFalse(BeepProbe.detect(room, quiet + tone(3000.0, 20, 0.3f) + quiet))
    }

    @Test
    fun `device identification reply`() {
        assertEquals("ac" to "Blue Star", FixyPrompts.parseDevice("""{"device":"ac","brand":"Blue Star"}"""))
        assertEquals("tv" to null, FixyPrompts.parseDevice("""```json {"device": "TV", "brand": null} ```"""))
        assertEquals("ac" to null, FixyPrompts.parseDevice("""{"device":"air conditioner","brand":"unknown"}"""))
        assertNull(FixyPrompts.parseDevice("I see a TV"))
    }
}
