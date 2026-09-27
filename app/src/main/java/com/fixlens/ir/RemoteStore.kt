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

    /** The device of this kind paired most recently, if any. */
    @Synchronized
    fun latest(kind: DeviceKind): RemoteProfile? = read().firstOrNull { it.kind == kind }

    /** Every paired device, newest first. */
    @Synchronized
    fun all(): List<RemoteProfile> = read()

    /** Saves [profile] as the newest pairing; a name the user gave the same device before is kept. */
    @Synchronized
    fun save(profile: RemoteProfile) {
        val devices = read()
        val same = devices.firstOrNull { it.kind == profile.kind && it.brand.equals(profile.brand, ignoreCase = true) }
        val named = if (profile.name == null && same?.name != null) profile.copy(name = same.name) else profile
        write(listOf(named) + devices.filterNot { it === same })
    }

    /** Renames a saved device in place (the list order stays). Blank clears the name. */
    @Synchronized
    fun rename(kind: DeviceKind, brand: String, name: String?) {
        write(read().map {
            if (it.kind == kind && it.brand.equals(brand, ignoreCase = true)) it.copy(name = name?.trim()?.takeIf(String::isNotEmpty)) else it
        })
    }

    /** Stores what changed on a saved device (the AC's last sent state) without moving it up the list. */
    @Synchronized
    fun update(profile: RemoteProfile) {
        val devices = read()
        if (devices.none { it.kind == profile.kind && it.modelId == profile.modelId }) return
        write(devices.map {
            if (it.kind == profile.kind && it.modelId == profile.modelId && it.brand.equals(profile.brand, ignoreCase = true)) {
                profile.copy(name = profile.name ?: it.name)
            } else {
                it
            }
        })
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
