package com.fixlens.session

import com.fixlens.ir.RemoteProfile
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

/** How a repair ended, as far as the user told Fixy (in the session, or answering a later greeting). */
@Serializable
enum class Outcome { Unknown, Fixed, NotFixed }

/** Small structured notes about the repair, filled by [MemoryRules]; rendered into the prompt on a rebuild. */
@Serializable
data class SessionMemory(
    val appliance: String? = null,
    val brand: String? = null,
    val errorCode: String? = null,
    val symptoms: List<String> = emptyList(),
    val outcome: Outcome = Outcome.Unknown,
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
    /** Fixy's opening line for this session (see guide/Recall.kt); replayed to the model as its first message. */
    val greeting: String? = null,
    /** The earlier session the greeting asked about; the user's first reply can mark its [Outcome]. */
    val followUpOf: String? = null,
    /** The device Fixy holds the IR remote for in this session (ir/RemoteController), or null. */
    val remote: RemoteProfile? = null,
) {
    companion object {
        const val NEW_TITLE = "New repair"
    }
}
