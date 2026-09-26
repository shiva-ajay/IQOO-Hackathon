package com.fixlens.guide

/** Fixy's persona and the prompt pieces, written as raw Qwen chat-template text (MNN's template is off). */
object FixyPrompts {

    // Round-1 persona prompt. No KB yet, so Fixy only describes and advises from what it sees.
    const val SYSTEM =
        "You are Fixy, a friendly repair helper inside the FixLens app. " +
            "Look at the camera image and answer the user's question like a calm, patient friend. " +
            "Max 2 short sentences. Always put safety first. " +
            "Your name is Fixy. Never call yourself an AI model, Qwen, or anything else. " +
            "Each question comes with the latest camera picture; earlier pictures may show a different view, " +
            "so answer about the latest picture unless the user asks about an earlier one. " +
            "When asked to point at something, output the box line first, then your spoken reply."

    /** Until the KB gives a target phrase (M4), Fixy points at whatever the question is about. */
    const val DEFAULT_TARGET = "the part I should look at for this question"

    /** Parts mode, no KB target: the exact things to touch, never the whole device. */
    const val DEFAULT_PARTS = "every exact part to act on for this question (a cap, a button, a cover), never the whole device"

    /**
     * How the pointing is asked for. [Parts] (default): a JSON list, a box per part (a point for a small single
     * part). Many tiny identical parts like screws get one box around their area: the VLM can't place each screw
     * reliably (docs/marker-tracking.md §6), so the count goes in the spoken step instead. [Contract] is CLAUDE.md §9's single box; [Native] is
     * Qwen3-VL's own single-box phrasing.
     */
    enum class GroundingStyle { Parts, Contract, Native }

    /** Appended to every question: the pointing JSON first, then the spoken reply. */
    fun grounding(target: String?, style: GroundingStyle): String {
        val find = target ?: DEFAULT_TARGET
        return when (style) {
            GroundingStyle.Parts ->
                "Find: ${target?.let { "\"$it\"" } ?: DEFAULT_PARTS}\n" +
                    "First: a JSON list, one entry per part: {\"bbox_2d\":[x1,y1,x2,y2],\"label\":\"...\"}, or " +
                    "{\"point_2d\":[x,y],\"label\":\"...\"} for a small single part. Many tiny identical parts (like screws) " +
                    "get one box around the area that holds them. [] if none.\n" +
                    "Then: one or two short sentences as Fixy."
            GroundingStyle.Contract ->
                "Find: \"$find\"\n" +
                    "First line: JSON only, {\"bbox_2d\":[x1,y1,x2,y2],\"label\":\"...\"} or {\"bbox_2d\":null}\n" +
                    "Then: one or two short sentences as Fixy."
            GroundingStyle.Native ->
                "Locate $find in the image and output its bbox coordinates in JSON on the first line, " +
                    "or {\"bbox_2d\":null} if nothing applies. Then answer in one or two short sentences as Fixy."
        }
    }

    /** Re-ground side request: only the JSON for the parts Fixy already pointed at. */
    fun locate(labels: List<String>): String =
        "Find again: ${labels.joinToString(", ")}. Output only a JSON list, one entry per part: " +
            "{\"point_2d\":[x,y],\"label\":\"...\"} for small parts, {\"bbox_2d\":[x1,y1,x2,y2],\"label\":\"...\"} " +
            "for bigger ones, or [] if they're not visible."

    const val TITLE_REQUEST =
        "Give this repair chat a short title of 2 to 4 words, like \"Fridge not cooling\" or " +
            "\"Car oil check\". Reply with the title only."

    fun system(notes: String?): String = buildString {
        append("<|im_start|>system\n").append(SYSTEM)
        if (notes != null) append("\n<session>\n").append(notes).append("\n</session>")
        append("<|im_end|>\n")
    }

    /** A past turn replayed as text only (its picture is not re-sent). */
    fun pastTurn(question: String, answer: String): String =
        "<|im_start|>user\n$question<|im_end|>\n<|im_start|>assistant\n$answer<|im_end|>\n"

    /** A new user turn, leaving the assistant turn open for generation. */
    fun userTurn(content: String): String = "<|im_start|>user\n$content<|im_end|>\n<|im_start|>assistant\n"

    /** Generation stops on the end token without feeding it back, so the next prompt closes the last answer. */
    const val CLOSE_ANSWER = "<|im_end|>\n"

    fun image(path: String) = "<img>$path</img>"

    /** Cleans the model's title; null if it's unusable. */
    fun cleanTitle(raw: String): String? {
        val title = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            .replace(Regex("""^(title\s*:\s*)""", RegexOption.IGNORE_CASE), "")
            .trim().trim('"', '\'', '“', '”', '.', '*', '#', ' ')
        val words = title.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 6 || title.length > 40) return null
        if (title.contains("fixy", ignoreCase = true)) return null
        return title.replaceFirstChar { it.uppercase() }
    }
}
