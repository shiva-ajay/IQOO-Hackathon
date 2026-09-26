package com.fixlens.session

import android.util.Log
import com.fixlens.app.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repair sessions on app-private storage: `<root>/<id>/session.json` plus the keyframes of its turns.
 * Nothing here ever leaves the phone.
 */
class SessionRepository(private val root: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun dir(id: String) = File(root, id)

    /** A new, unsaved session. It's written to disk with its first turn, so empty sessions never show up. */
    fun create(): RepairSession {
        val now = System.currentTimeMillis()
        return RepairSession(id = "s$now", created = now, updated = now)
    }

    suspend fun list(): List<RepairSession> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty()
            .mapNotNull { read(File(it, FILE)) }
            .filter { it.turns.isNotEmpty() }
            .sortedByDescending { it.updated }
    }

    suspend fun load(id: String): RepairSession? = withContext(Dispatchers.IO) { read(File(dir(id), FILE)) }

    /** Writes the session atomically (temp file + rename), so a crash never leaves half a file. */
    suspend fun save(session: RepairSession) = withContext(Dispatchers.IO) {
        val dir = dir(session.id).apply { mkdirs() }
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(json.encodeToString(RepairSession.serializer(), session))
        if (!tmp.renameTo(File(dir, FILE))) Log.e(TAG, "Session save failed: ${session.id}")
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dir(id).deleteRecursively() }

    private fun read(file: File): RepairSession? = runCatching {
        if (file.exists()) json.decodeFromString(RepairSession.serializer(), file.readText()) else null
    }.onFailure { Log.e(TAG, "Unreadable session $file", it) }.getOrNull()

    private companion object {
        const val FILE = "session.json"
    }
}
