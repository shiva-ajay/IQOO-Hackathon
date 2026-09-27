package com.fixlens.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real app/src/main/assets/ir/ built by tools/ir/build_ir_assets.py, as the phone does. */
class IrCatalogAssetsTest {
    private val dir = listOf(File("src/main/assets/ir"), File("app/src/main/assets/ir")).first { it.isDirectory }
    private val catalog = IrCatalog.load { name -> File(dir, name).inputStream() }

    @Test
    fun `every device has brands and every fixed code is sendable`() {
        for (kind in DeviceKind.entries) {
            val brands = catalog.brands(kind)
            assertTrue("$kind brands", brands.size > 50)
            assertTrue("$kind featured", catalog.featured(kind).isNotEmpty())
            assertTrue("$kind common", catalog.common(kind).isNotEmpty())
            if (kind == DeviceKind.Ac) {
                brands.flatMap { it.models }.forEach { m -> assertTrue(m.id, File(dir, "ac/${m.ac}").isFile) }
                continue
            }
            for (m in brands.flatMap { it.models }) {
                for (id in m.keys.keys) {
                    val p = catalog.pattern(m, Button.of(id)!!)!!
                    assertNotNull("${m.id} $id", p.validOrNull())
                    assertTrue("${m.id} $id carrier ${p.carrierHz}", p.carrierHz in 30000..60000)
                }
            }
        }
    }

    @Test
    fun `an LG TV's mute is the NEC code Flipper would send`() {
        val lg = catalog.brand(DeviceKind.Tv, "LG")!!
        val model = lg.models.first { it.id == "flipper:TVs/LG/LG_24LJ4840.ir" }
        val mute = catalog.pattern(model, Button.Mute)!!
        assertEquals(38000, mute.carrierHz)
        // NEC: 9 ms mark, 4.5 ms space, then 32 bits.
        assertArrayEquals(intArrayOf(9000, 4500), mute.timings.copyOf(2))
        assertEquals(1 + 2 + 32 * 2, mute.timings.size.coerceAtMost(67))
    }

    @Test
    fun `brands the demo needs are there`() {
        for ((kind, name) in listOf(DeviceKind.Tv to "Samsung", DeviceKind.Tv to "Sony", DeviceKind.Ac to "Voltas",
            DeviceKind.Ac to "Daikin", DeviceKind.Ac to "Blue Star", DeviceKind.Projector to "Epson", DeviceKind.Fan to "Atomberg")) {
            assertNotNull("$kind $name", catalog.brand(kind, name))
        }
        assertEquals("Blue Star", catalog.matchBrand(DeviceKind.Ac, "it's a bluestar").best)
    }
}
