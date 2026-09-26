package com.fixlens.session

import kotlinx.serialization.Serializable

/** One question and Fixy's answer. Only completed turns are stored; cancelled ones are dropped. */
@Serializable
data class Turn(
    val n: Int,
    val question: String,
    val answer: String,
    /** Keyframe file name inside the session folder, or null if the camera had no frame. */
    val keyframe: String? = null,
    val ts: Long,
    val ms: Long = 0,
)

/** Small structured notes about the repair, filled by [MemoryRules]; rendered into the prompt on a rebuild. */
@Serializable
data class SessionMemory(
    val appliance: String? = null,
    val brand: String? = null,
    val errorCode: String? = null,
    val symptoms: List<String> = emptyList(),
)

@Serializable
enum class TitleSource { None, Keyword, Model, User }

@Serializable
data class RepairSession(
    val id: String,
    val title: String = NEW_TITLE,
    val titleSource: TitleSource = TitleSource.None,
    val created: Long,
    val updated: Long,
    val memory: SessionMemory = SessionMemory(),
    val turns: List<Turn> = emptyList(),
) {
    companion object {
        const val NEW_TITLE = "New repair"
    }
}
