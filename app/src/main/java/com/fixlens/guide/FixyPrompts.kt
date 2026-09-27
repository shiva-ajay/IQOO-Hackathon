package com.fixlens.guide

/** Fixy's persona and the prompt pieces, written as raw Qwen chat-template text (MNN's template is off). */
object FixyPrompts {

    // Persona prompt (docs/fixy-memory.md §2). Kept free of device examples: a small model repeats whatever the
    // prompt mentions, so naming car parts here made Fixy talk about oil while looking at a laptop.
    const val SYSTEM =
        "You are Fixy, the helper inside the FixLens app. You talk like a friendly, easygoing person standing " +
            "next to the user and looking through their phone camera with them.\n" +
            "How you talk:\n" +
            "- Spoken aloud: 1 or 2 short, natural sentences with contractions. No lists, no headings.\n" +
            "- Answer exactly what was asked, nothing more. A casual question gets a casual answer. Don't turn " +
            "every reply into a repair, and don't end every reply with a question or an offer to help.\n" +
            "- Only talk about what's in the latest picture or what the user brought up. Never mention a device, " +
            "part or problem that isn't there.\n" +
            "- If asked what you do: you look through the camera, help figure out what's wrong with cars, home " +
            "appliances and gadgets, and point at the exact part to check. One sentence.\n" +
            "- Your name is Fixy. Never call yourself an AI model, Qwen, or anything else.\n" +
            "Rules for repairs:\n" +
            "- Safety first. Wiring, gas, and opening mains-powered or sealed parts are jobs for a technician; " +
            "say so kindly.\n" +
            "- Never invent parts, values, error codes or past events. If you're not sure, say so and suggest " +
            "a technician.\n" +
            "- A question may come with the latest camera picture; earlier pictures may show something else, so " +
            "answer about the latest one.\n" +
            "- When asked to point at something, output the JSON first, then your spoken reply."

    /** Only while `<past_repairs>` is in the prompt, i.e. for the user's reply to a greeting that asked after it. */
    private const val PAST_REPAIRS_RULE =
        "- <past_repairs> is the earlier repair your greeting just asked about. If the user answers about it, " +
            "reply briefly from that note only; otherwise ignore it. Once answered, it's done: don't bring it up again."

    /** Small talk: no picture, no pointing. */
    const val CHAT_TURN = "(Just chatting: reply naturally in one short sentence. No repair advice.)"

    /** "What do you see?": describe the picture, no pointing and no repair pitch. */
    const val LOOK_TURN =
        "(Say what you see in this picture in one or two short sentences, like telling a friend. Only what's " +
            "really there; no repair steps unless asked.)"

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
                    "Then: one or two short sentences as Fixy, answering exactly what was asked."
            GroundingStyle.Contract ->
                "Find: \"$find\"\n" +
                    "First line: JSON only, {\"bbox_2d\":[x1,y1,x2,y2],\"label\":\"...\"} or {\"bbox_2d\":null}\n" +
                    "Then: one or two short sentences as Fixy."
            GroundingStyle.Native ->
                "Locate $find in the image and output its bbox coordinates in JSON on the first line, " +
                    "or {\"bbox_2d\":null} if nothing applies. Then answer in one or two short sentences as Fixy."
        }
    }

    /** Guided step (M4): only the JSON for the step's target phrase from the KB. */
    fun pointAt(target: String): String =
        "Find: \"$target\". Output only a JSON list, one entry per part: {\"bbox_2d\":[x1,y1,x2,y2],\"label\":\"...\"}, " +
            "or {\"point_2d\":[x,y],\"label\":\"...\"} for a small single part. Many tiny identical parts (like screws) " +
            "get one box around the area that holds them. [] if it's not visible."

    /** Guided step auto-check: is the step's visible sign of completion there? */
    fun verify(sign: String): String = "Look at the picture. Is this true: \"$sign\"? Answer only yes or no."

    /** Remote side request: which appliance is in view and the brand printed on it (ir/RemoteController). */
    const val IDENTIFY_DEVICE =
        "Look at the picture. Which appliance is the main subject, and what brand name is printed on it? " +
            "Reply with JSON only: {\"device\":\"ac|tv|projector|fan|other\",\"brand\":\"<the brand as printed>\"}. " +
            "Use \"brand\":null if no brand name is readable. Never guess a brand."

    /** (device, brand) from the [IDENTIFY_DEVICE] reply; brand null when unreadable. Null if it isn't JSON. */
    fun parseDevice(raw: String): Pair<String, String?>? {
        val json = Regex("""\{[^{}]*\}""").find(raw)?.value ?: return null
        fun field(name: String) = Regex("""\"$name\"\s*:\s*(null|\"([^\"]*)\")""").find(json)?.groupValues?.get(2)
        val device = field("device")?.trim()?.lowercase() ?: return null
        val kind = when {
            device == "ac" || device.contains("air") || device.contains("conditioner") -> "ac"
            device.contains("tv") || device.contains("television") -> "tv"
            device.contains("projector") -> "projector"
            device.contains("fan") -> "fan"
            else -> "other"
        }
        val brand = field("brand")?.trim()?.takeUnless { it.isEmpty() || it.equals("unknown", true) || it.equals("null", true) }
        return kind to brand
    }

    /** Re-ground side request: only the JSON for the parts Fixy already pointed at. */
    fun locate(labels: List<String>): String =
        "Find again: ${labels.joinToString(", ")}. Output only a JSON list, one entry per part: " +
            "{\"point_2d\":[x,y],\"label\":\"...\"} for small parts, {\"bbox_2d\":[x1,y1,x2,y2],\"label\":\"...\"} " +
            "for bigger ones, or [] if they're not visible."

    const val TITLE_REQUEST =
        "Give this repair chat a short title of 2 to 4 words, like \"Fridge not cooling\" or " +
            "\"Car oil check\". Reply with the title only."

    /**
     * The start of every rebuilt prompt: persona, this session's [notes], the [pastRepairs] block (guide/Recall.kt)
     * with its rule, and the session's [greeting] as Fixy's first message, so a reply like "yes, it's fine now"
     * makes sense.
     */
    fun system(notes: String?, pastRepairs: String? = null, greeting: String? = null): String = buildString {
        append("<|im_start|>system\n").append(SYSTEM)
        if (pastRepairs != null) append('\n').append(PAST_REPAIRS_RULE)
        if (notes != null) append("\n<session>\n").append(notes).append("\n</session>")
        if (pastRepairs != null) append("\n<past_repairs>\n").append(pastRepairs).append("\n</past_repairs>")
        append("<|im_end|>\n")
        if (greeting != null) append("<|im_start|>assistant\n").append(greeting).append("<|im_end|>\n")
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
