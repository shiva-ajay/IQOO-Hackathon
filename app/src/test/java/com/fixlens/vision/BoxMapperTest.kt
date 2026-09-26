package com.fixlens.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoxMapperTest {

    private fun assertBox(expected: PxBox, actual: PxBox?, eps: Float = 0.01f) {
        assertNotNull(actual)
        actual!!
        assertEquals("left", expected.left, actual.left, eps)
        assertEquals("top", expected.top, actual.top, eps)
        assertEquals("right", expected.right, actual.right, eps)
        assertEquals("bottom", expected.bottom, actual.bottom, eps)
    }

    // Portrait keyframe as the app makes it (upright, sides multiples of 32).
    private val kfW = 192
    private val kfH = 448

    @Test fun `normalized 1000 scales each axis by its own size`() {
        val b = BoxMapper.modelToKeyframe(ModelBox(250f, 500f, 750f, 1000f), CoordScale.NORMALIZED_1000, kfW, kfH)
        assertBox(PxBox(48f, 224f, 144f, 448f), b)
    }

    @Test fun `absolute pixels pass through`() {
        val b = BoxMapper.modelToKeyframe(ModelBox(10f, 20f, 100f, 200f), CoordScale.ABSOLUTE_PIXELS, kfW, kfH)
        assertBox(PxBox(10f, 20f, 100f, 200f), b)
    }

    @Test fun `the same numbers mean different places in the two modes`() {
        // A centred box in 0..1000 terms, read as pixels, lands far off-centre: this is what the drawn test box catches.
        val norm = BoxMapper.modelToKeyframe(ModelBox(400f, 400f, 600f, 600f), CoordScale.NORMALIZED_1000, kfW, kfH)!!
        val px = BoxMapper.modelToKeyframe(ModelBox(40f, 40f, 60f, 60f), CoordScale.ABSOLUTE_PIXELS, kfW, kfH)!!
        assertEquals(kfW / 2f, norm.centerX, 0.01f)
        assertEquals(kfH / 2f, norm.centerY, 0.01f)
        assertEquals(50f, px.centerX, 0.01f)
    }

    @Test fun `landscape keyframe`() {
        val b = BoxMapper.modelToKeyframe(ModelBox(0f, 0f, 500f, 500f), CoordScale.NORMALIZED_1000, 448, 256)
        assertBox(PxBox(0f, 0f, 224f, 128f), b)
    }

    @Test fun `swapped corners are fixed`() {
        val b = BoxMapper.modelToKeyframe(ModelBox(750f, 1000f, 250f, 500f), CoordScale.NORMALIZED_1000, kfW, kfH)
        assertBox(PxBox(48f, 224f, 144f, 448f), b)
    }

    @Test fun `out of range is clamped`() {
        val b = BoxMapper.modelToKeyframe(ModelBox(-50f, 900f, 1200f, 1100f), CoordScale.NORMALIZED_1000, kfW, kfH)
        assertBox(PxBox(0f, 403.2f, 192f, 448f), b)
    }

    @Test fun `degenerate boxes are rejected`() {
        assertNull(BoxMapper.modelToKeyframe(ModelBox(100f, 100f, 100f, 400f), CoordScale.NORMALIZED_1000, kfW, kfH))
        assertNull(BoxMapper.modelToKeyframe(ModelBox(100f, 100f, 101f, 400f), CoordScale.ABSOLUTE_PIXELS, kfW, kfH))
        assertNull(BoxMapper.modelToKeyframe(ModelBox(1100f, 100f, 1300f, 400f), CoordScale.NORMALIZED_1000, kfW, kfH))
        assertNull(BoxMapper.modelToKeyframe(ModelBox(Float.NaN, 1f, 2f, 3f), CoordScale.NORMALIZED_1000, kfW, kfH))
        assertNull(BoxMapper.modelToKeyframe(ModelBox(1f, 1f, 50f, 50f), CoordScale.NORMALIZED_1000, 0, kfH))
    }

    @Test fun `point answer gets a small square box`() {
        val b = BoxMapper.modelToKeyframe(ModelBox(500f, 500f, 500f, 500f, isPoint = true), CoordScale.NORMALIZED_1000, kfW, kfH)!!
        assertEquals(96f, b.centerX, 0.01f)
        assertEquals(224f, b.centerY, 0.01f)
        assertEquals(kfW * BoxMapper.POINT_BOX_FRACTION, b.width, 0.01f)
        assertEquals(b.width, b.height, 0.01f)
    }

    @Test fun `keyframe to analysis is a per-axis scale`() {
        // Keyframe 192x448 and analysis 216x480 come from the same upright crop.
        val a = BoxMapper.keyframeToAnalysis(PxBox(48f, 112f, 144f, 336f), kfW, kfH, 216, 480)
        assertBox(PxBox(54f, 120f, 162f, 360f), a)
    }

    @Test fun `fill center with matching aspect is a pure scale`() {
        // Analysis 216x480 on a 1440x3200 view (same 9:20 aspect).
        val t = BoxMapper.fillCenter(216, 480, 1440f, 3200f)
        assertEquals(1440f / 216, t.scale, 1e-4f)
        assertEquals(0f, t.dx, 1e-3f)
        assertEquals(0f, t.dy, 1e-3f)
    }

    @Test fun `test box 25 to 75 percent lands centred and half size`() {
        val aw = 216
        val ah = 480
        val t = BoxMapper.fillCenter(aw, ah, 1440f, 3200f)
        val v = t.map(PxBox(aw * 0.25f, ah * 0.25f, aw * 0.75f, ah * 0.75f))
        assertBox(PxBox(360f, 800f, 1080f, 2400f), v, eps = 0.5f)
    }

    @Test fun `fill center crops the wider source`() {
        // A 4:3 upright frame (480x640) on a 9:20 view: height fits, sides overflow equally.
        val t = BoxMapper.fillCenter(480, 640, 1440f, 3200f)
        assertEquals(5f, t.scale, 1e-4f)
        assertEquals((1440f - 2400f) / 2f, t.dx, 1e-3f)
        assertEquals(0f, t.dy, 1e-3f)
        // The frame centre stays at the view centre.
        assertEquals(720f, t.mapX(240f), 1e-3f)
        assertEquals(1600f, t.mapY(320f), 1e-3f)
    }

    @Test fun `fill center with a landscape view crops top and bottom`() {
        val t = BoxMapper.fillCenter(480, 640, 2000f, 1000f)
        assertEquals(2000f / 480, t.scale, 1e-4f)
        assertEquals(0f, t.dx, 1e-3f)
        assertEquals((1000f - 640 * t.scale) / 2f, t.dy, 1e-3f)
    }
}
