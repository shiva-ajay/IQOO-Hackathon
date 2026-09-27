package com.fixlens.alerts

import android.util.Log
import com.fixlens.app.TAG
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The scheduled alerts (`files/alerts.json`), read by the app and by [AlertReceiver] when an alarm goes off.
 * Small file, callers use IO threads. Synchronized on the class: the receiver and the app may use two instances.
 */
class AlertStore(private val file: File) {
    @Serializable
    private class Saved(val alerts: List<Alert> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun all(): List<Alert> = synchronized(LOCK) { read() }

    fun get(id: String): Alert? = all().firstOrNull { it.id == id }

    /** Applies [transform] to the list and saves it; returns the new list. */
    fun edit(transform: (List<Alert>) -> List<Alert>): List<Alert> = synchronized(LOCK) {
        transform(read()).also(::write)
    }

    private fun read(): List<Alert> = runCatching {
        if (file.exists()) json.decodeFromString<Saved>(file.readText()).alerts else emptyList()
    }.onFailure { Log.w(TAG, "Alerts file unreadable, starting empty", it) }.getOrDefault(emptyList())

    private fun write(alerts: List<Alert>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), Saved(alerts.takeLast(MAX_ALERTS))))
            tmp.renameTo(file)
        }.onFailure { Log.e(TAG, "Couldn't save alerts", it) }
    }

    private companion object {
        val LOCK = Any()
        const val MAX_ALERTS = 50
    }
}
