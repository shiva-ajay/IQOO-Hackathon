package com.fixlens.guide

import com.fixlens.session.SessionMemory

/**
 * Cheap, deterministic note-taking for a session: no VLM call. Pulls the appliance, brand, error code and
 * symptoms out of each question (and, for the appliance, Fixy's answer).
 */
object MemoryRules {

    private val APPLIANCES = listOf(
        "Washing machine" to Regex("""washing machine|washer|front[- ]load|top[- ]load|\bdrum\b|laundry"""),
        "Refrigerator" to Regex("""refrigerator|fridge|freezer"""),
        "Car engine" to Regex("""\bcar\b|engine|bonnet|\bhood\b|dipstick|coolant|radiator|battery terminal|wiper fluid|washer fluid"""),
        "Dishwasher" to Regex("""dishwasher"""),
        "Microwave" to Regex("""microwave|\boven\b"""),
        "Air conditioner" to Regex("""air condition|\bac\b|split unit"""),
        "Water purifier" to Regex("""water purifier|\bro\b purifier|purifier"""),
        "Water heater" to Regex("""geyser|water heater"""),
        "Inverter" to Regex("""inverter|\bups\b"""),
    )

    private val BRANDS = listOf(
        "Ariston", "LG", "Samsung", "Whirlpool", "IFB", "Bosch", "Haier", "Godrej", "Panasonic", "Voltas",
        "Heran", "Maruti", "Suzuki", "Hyundai", "Tata", "Honda", "Toyota", "Mahindra", "Kia", "Chevrolet",
    )

    private val SYMPTOMS = listOf(
        "not draining" to Regex("""not drain|won'?t drain|doesn'?t drain|water (is )?(stuck|standing)"""),
        "not spinning" to Regex("""not spin|won'?t spin|doesn'?t spin"""),
        "leaking" to Regex("""leak"""),
        "not cooling" to Regex("""not cool|isn'?t cold|not cold|warm inside"""),
        "strange noise" to Regex("""noise|noisy|rattl|grinding|squeal|clicking"""),
        "won't start" to Regex("""won'?t start|not start|doesn'?t start|won'?t turn on|not turning on|no power|dead"""),
        "burning smell" to Regex("""burning|smell"""),
        "warning light" to Regex("""warning light|light is on|light came on|blink|flashing"""),
        "overheating" to Regex("""overheat|too hot|temperature gauge"""),
        "door problem" to Regex("""door (won'?t|will not|doesn'?t|not) (open|close|lock)|door lock"""),
    )

    // "error E4", "code OE", "showing F 05" ... and bare E/F codes like "E4".
    private val CODE_AFTER_WORD = Regex("""(?i)\b(?:error|code|showing|shows|says|display(?:s|ing)?)\s+(?:code\s+)?([a-z]{1,2})[\s-]?(\d{1,3})\b""")
    private val BARE_CODE = Regex("""\b([EF])[\s-]?(\d{1,2})\b""")
    private val LETTER_CODE = Regex("""\b(OE|UE|LE|DE|dE|IE|HE|PE|FE|tE|SUD|UNB)\b""")

    fun update(memory: SessionMemory, question: String, answer: String): SessionMemory {
        val q = question.lowercase()
        val appliance = memory.appliance
            ?: APPLIANCES.firstOrNull { it.second.containsMatchIn(q) }?.first
            ?: APPLIANCES.firstOrNull { it.second.containsMatchIn(answer.lowercase()) }?.first
        val brand = BRANDS.firstOrNull { Regex("""\b${it.lowercase()}\b""").containsMatchIn(q) } ?: memory.brand
            ?: BRANDS.firstOrNull { Regex("""\b$it\b""").containsMatchIn(answer) }
        val symptoms = (memory.symptoms + SYMPTOMS.filter { it.second.containsMatchIn(q) }.map { it.first })
            .distinct().take(MAX_SYMPTOMS)
        return memory.copy(
            appliance = appliance,
            brand = brand,
            errorCode = errorCode(question) ?: memory.errorCode,
            symptoms = symptoms,
        )
    }

    fun errorCode(text: String): String? {
        CODE_AFTER_WORD.find(text)?.let { return (it.groupValues[1] + it.groupValues[2]).uppercase() }
        BARE_CODE.find(text)?.let { return (it.groupValues[1] + it.groupValues[2]).uppercase() }
        return LETTER_CODE.find(text)?.value?.uppercase()
    }

    /** Instant title from the notes, shown until (or instead of) the model's title. */
    fun keywordTitle(memory: SessionMemory): String? {
        val appliance = memory.appliance ?: return memory.errorCode?.let { "Error $it" }
        return when {
            memory.errorCode != null -> "$appliance · ${memory.errorCode}"
            appliance == "Car engine" -> "Car engine check"
            else -> "$appliance repair"
        }
    }

    /** The notes as a few short prompt lines, or null when there is nothing worth saying yet. */
    fun render(memory: SessionMemory): String? {
        val lines = buildList {
            memory.appliance?.let { add("appliance: $it" + (memory.brand?.let { b -> " ($b)" } ?: "")) }
                ?: memory.brand?.let { add("brand: $it") }
            memory.errorCode?.let { add("error code: $it") }
            if (memory.symptoms.isNotEmpty()) add("symptoms: ${memory.symptoms.joinToString(", ")}")
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private const val MAX_SYMPTOMS = 5
}
