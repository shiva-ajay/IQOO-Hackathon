package com.fixlens.ir

import android.util.Log
import com.fixlens.app.TAG
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Devices paired before, across sessions (`files/remotes.json`), so taking the same brand's remote again
 * skips pairing. One entry per device kind + brand; the newest pairing wins. Small file: callers use IO threads.
 */
class RemoteStore(private val file: File) {
    @Serializable
    private class Saved(val devices: List<RemoteProfile> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun find(kind: DeviceKind, brand: String): RemoteProfile? =
        read().firstOrNull { it.kind == kind && it.brand.equals(brand, ignoreCase = true) }

    @Synchronized
    fun save(profile: RemoteProfile) {
        write(listOf(profile) + read().filterNot { it.kind == profile.kind && it.brand.equals(profile.brand, ignoreCase = true) })
    }

    @Synchronized
    fun forget(kind: DeviceKind, brand: String) {
        write(read().filterNot { it.kind == kind && it.brand.equals(brand, ignoreCase = true) })
    }

    private fun read(): List<RemoteProfile> = runCatching {
        if (file.exists()) json.decodeFromString<Saved>(file.readText()).devices else emptyList()
    }.onFailure { Log.w(TAG, "Paired devices file unreadable, starting empty", it) }.getOrDefault(emptyList())

    private fun write(devices: List<RemoteProfile>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), Saved(devices.take(MAX_DEVICES))))
            tmp.renameTo(file)
        }.onFailure { Log.e(TAG, "Couldn't save paired devices", it) }
    }

    private companion object {
        const val MAX_DEVICES = 20
    }
}
