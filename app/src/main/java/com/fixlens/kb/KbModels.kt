package com.fixlens.kb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The curated knowledge base (`assets/kb/fixlens_kb.json`, schema in CLAUDE.md §7). Text is spoken verbatim. */
@Serializable
data class KnowledgeBase(
    @SerialName("kb_version") val version: String,
    val entries: List<KbEntry>,
)

@Serializable
data class KbEntry(
    val id: String,
    val appliance: String,
    val brand: String = "generic",
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("code_aliases") val codeAliases: List<String> = emptyList(),
    val title: String,
    val meaning: String = "",
    val symptoms: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val severity: Severity,
    @SerialName("escalate_if") val escalateIf: List<String> = emptyList(),
    val safety: List<String> = emptyList(),
    val steps: List<KbStep> = emptyList(),
    val source: String = "",
)

@Serializable
data class KbStep(
    val n: Int,
    /** Spoken and shown word for word. */
    val say: String,
    /** A descriptive grounding phrase for the VLM ("yellow ring handle of the engine oil dipstick"), or null. */
    val target: String? = null,
    /** Something visible that shows the step is done ("the oil filler cap is off"), checked by the VLM; or null. */
    val verify: String? = null,
    val caution: String? = null,
)

@Serializable
enum class Severity {
    @SerialName("diy") Diy,
    @SerialName("caution") Caution,
    @SerialName("call_technician") CallTechnician,
}
