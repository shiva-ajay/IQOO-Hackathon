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
            "so answer about the latest picture unless the user asks about an earlier one."

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
