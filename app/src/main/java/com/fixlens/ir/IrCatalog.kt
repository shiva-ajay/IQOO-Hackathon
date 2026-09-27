package com.fixlens.ir

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The IR code catalog in `assets/ir/` (built by tools/ir/build_ir_assets.py from IRext + Flipper-IRDB):
 * device → brand → models. TV/projector/fan models map a button to a pattern in `patterns.bin`; AC models name
 * an IRext binary under `ac/`, from which any AC state is built at send time.
 */
class IrCatalog private constructor(
    val version: String,
    private val devices: Map<DeviceKind, DeviceCodes>,
    private val carriers: IntArray,
    private val offsets: IntArray,
    private val data: ShortArray,
) {
    @Serializable
    class ModelCodes(val id: String, val keys: Map<String, Int> = emptyMap(), val ac: String? = null)

    @Serializable
    class BrandCodes(val brand: String, val models: List<ModelCodes>, val spoken: List<String> = emptyList())

    @Serializable
    class DeviceCodes(
        val brands: List<BrandCodes>,
        /** One model per most widely shared code, for "try common codes" when the brand has no match. */
        val common: List<ModelCodes> = emptyList(),
        /** Brands the picker shows first. */
        val featured: List<String> = emptyList(),
    )

    @Serializable
    private class CatalogFile(val version: String, val devices: Map<String, DeviceCodes>)

    fun brands(kind: DeviceKind): List<BrandCodes> = devices[kind]?.brands.orEmpty()

    fun brand(kind: DeviceKind, name: String): BrandCodes? = brands(kind).firstOrNull { it.brand.equals(name, ignoreCase = true) }

    fun featured(kind: DeviceKind): List<String> = devices[kind]?.featured.orEmpty()

    fun common(kind: DeviceKind): List<ModelCodes> = devices[kind]?.common.orEmpty()

    /** Brand names for [query] (typed, spoken or read off the device), best first. */
    fun matchBrand(kind: DeviceKind, query: String): BrandMatcher.Result =
        BrandMatcher.match(query, brands(kind).map { BrandMatcher.Candidate(it.brand, it.spoken) })

    /** A fixed button's signal for a TV/projector/fan model, or null if the model has no such button. */
    fun pattern(model: ModelCodes, button: Button): IrPattern? {
        val index = model.keys[button.id] ?: return null
        if (index !in carriers.indices) return null
        val from = offsets[index]
        val to = if (index + 1 < offsets.size) offsets[index + 1] else data.size
        val timings = IntArray(to - from) { data[from + it].toInt() and 0xFFFF }
        return IrPattern(carriers[index], timings)
    }

    companion object {
        private const val DIR = "ir"
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads the catalog (~2 MB); call off the main thread. Throws if the assets are missing or broken. */
        fun load(assets: AssetManager): IrCatalog = load { name -> assets.open("$DIR/$name") }

        /** [open] gives a file of `assets/ir/` by name (unit tests read the real files from disk). */
        fun load(open: (String) -> InputStream): IrCatalog {
            val file = open("catalog.json").use { json.decodeFromString<CatalogFile>(it.readBytes().decodeToString()) }
            val devices = file.devices.mapNotNull { (id, codes) -> DeviceKind.of(id)?.let { it to codes } }.toMap()
            val bytes = open("patterns.bin").use { DataInputStream(it).readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            check(magic.decodeToString() == "FXIR") { "patterns.bin: bad header" }
            check(buf.short.toInt() == 1) { "patterns.bin: unknown version" }
            val count = buf.int
            val carriers = IntArray(count)
            val offsets = IntArray(count)
            // Upper bound for all timings: what's left of the file, 2 bytes each.
            val data = ShortArray(buf.remaining() / 2)
            var used = 0
            for (i in 0 until count) {
                carriers[i] = buf.int
                val n = buf.short.toInt() and 0xFFFF
                offsets[i] = used
                for (j in 0 until n) data[used + j] = buf.short
                used += n
            }
            return IrCatalog(file.version, devices, carriers, offsets, data.copyOf(used))
        }

        fun acBinary(assets: AssetManager, name: String): ByteArray = assets.open("$DIR/ac/$name").use { it.readBytes() }
    }
}

/** Finds a catalog brand in free text ("it's a voltas", "L G", "Samsang"). Pure, unit-tested. */
object BrandMatcher {
    class Candidate(val name: String, val spoken: List<String> = emptyList())

    /** [exact]: the text names this brand outright; otherwise [names] are guesses to offer, best first. */
    data class Result(val names: List<String>, val exact: Boolean) {
        val best: String? get() = names.firstOrNull()
    }

    fun match(query: String, candidates: List<Candidate>, limit: Int = 3): Result {
        val q = norm(query)
        if (q.isEmpty()) return Result(emptyList(), false)
        val words = query.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        // Whole-word phrases in the text ("my lg tv" contains "lg"); longest brand wins ("blue star" over "star").
        val contained = candidates.filter { c ->
            (listOf(c.name) + c.spoken).any { alias -> containsWords(words, alias) }
        }.sortedByDescending { norm(it.name).length }
        if (contained.isNotEmpty()) return Result(contained.take(limit).map { it.name }, exact = true)
        if (candidates.any { norm(it.name) == q }) return Result(listOf(candidates.first { norm(it.name) == q }.name), true)
        // Near misses: the best-scoring word or two-word run of the text against each brand.
        val runs = (words.filter { it !in COMMON_WORDS } + words.zipWithNext { a, b -> a + b }).filter { it.length >= 2 } +
            listOf(q).filter { it !in COMMON_WORDS }
        if (runs.isEmpty()) return Result(emptyList(), false)
        val scored = candidates.map { c ->
            val targets = (listOf(c.name) + c.spoken).map(::norm).filter { it.isNotEmpty() }
            c.name to runs.maxOf { r -> targets.maxOf { t -> similarity(r, t) } }
        }.filter { it.second >= 0.72 }.sortedByDescending { it.second }
        return Result(scored.take(limit).map { it.first }, exact = false)
    }

    private fun containsWords(words: List<String>, alias: String): Boolean {
        val a = alias.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        if (a.isEmpty() || a.size > words.size) return false
        // A one-word brand that is also an everyday word ("Best", "General") never matches inside a sentence.
        if (a.size == 1 && a[0] in COMMON_WORDS) return false
        // Short aliases ("mi", "vu") only as a whole word, never inside another.
        return words.windowed(a.size).any { it == a }
    }

    private val COMMON_WORDS = setOf(
        "a", "an", "the", "on", "off", "it", "my", "me", "is", "to", "in", "of", "and", "or", "up", "down",
        "best", "general", "android", "home", "smart", "star", "blue", "air", "cool", "fan", "tv", "ac", "remote",
        "control", "universal", "generic", "other", "unknown", "national", "power", "mode", "one", "go", "max",
        "pro", "plus", "next", "life", "day", "sun", "sky", "world", "first", "new", "royal", "prime", "classic",
        "light", "better", "visibility", "aim", "apex", "amazon", "sharp", "orient", "camel", "pioneer", "big",
    )

    fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** 1 - Levenshtein distance / longer length. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
