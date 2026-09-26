package com.fixlens.guide

import com.fixlens.session.Outcome
import com.fixlens.session.RepairSession
import com.fixlens.session.SessionMemory

/**
 * Cheap, deterministic note-taking for a session: no VLM call. Pulls the appliance, brand, error code and
 * symptoms out of each question (and, for the appliance, Fixy's answer).
 */
object MemoryRules {

    private class Appliance(val name: String, val kbId: String, val words: Regex)

    // First match wins, so the specific ones come first: "inverter battery terminal" is an inverter, "bike
    // engine" a bike, "car AC" a car, and "AC remote" an AC.
    private val APPLIANCES = listOf(
        Appliance("Washing machine", "washing_machine", Regex("""washing machine|washer(?! fluid)|front[- ]load|top[- ]load|\bdrum\b|laundry""")),
        Appliance("Refrigerator", "refrigerator", Regex("""refrigerator|fridge|freezer""")),
        Appliance("Dishwasher", "dishwasher", Regex("""dishwasher""")),
        Appliance("Microwave", "microwave", Regex("""microwave|\boven\b""")),
        Appliance("Water purifier", "water_purifier", Regex("""water purifier|\bro\b purifier|purifier""")),
        Appliance("Water heater", "water_heater", Regex("""geyser|water heater""")),
        Appliance("Inverter", "inverter", Regex("""inverter|\bups\b""")),
        Appliance("Printer", "printer", Regex("""printer|cartridge|paper jam|\btoner\b""")),
        Appliance("Laptop", "laptop", Regex("""laptop|notebook|macbook|touchpad|trackpad""")),
        Appliance("Bike", "bike", Regex("""\bbike|motorbike|motorcycle|scooter|scooty|two[- ]wheeler|\bchain\b|kick start|centre stand|center stand|side stand""")),
        Appliance("Car engine", "car", Regex("""\bcar\b|engine|bonnet|\bhood\b|dipstick|coolant|radiator|battery terminal|wiper fluid|washer fluid|dashboard""")),
        Appliance("Air conditioner", "air_conditioner", Regex("""air condition|\bac\b|split unit""")),
        Appliance("Television", "television", Regex("""television|\btv\b|remote control""")),
    )

    /** The KB `appliance` ids the notes can name. One with no entries (a printer) finds nothing rather than everything. */
    val KB_APPLIANCES: Set<String> = APPLIANCES.map { it.kbId }.toSet()

    /**
     * The KB appliance and brand to look an entry up under: what [text] names, else the session's notes.
     * Null appliance = search every appliance.
     */
    fun kbContext(text: String, memory: SessionMemory?): Pair<String?, String?> {
        val t = text.lowercase()
        val appliance = APPLIANCES.firstOrNull { it.words.containsMatchIn(t) }
            ?: memory?.appliance?.let { name -> APPLIANCES.firstOrNull { it.name == name } }
        return appliance?.kbId to (brandIn(t) ?: memory?.brand)
    }

    private fun brandIn(lower: String): String? = BRANDS.firstOrNull { Regex("""\b${it.lowercase()}\b""").containsMatchIn(lower) }

    // Checked before FIXED, so "still not working" never reads as "working".
    private val NOT_FIXED = Regex("""still (not|isn'?t|doesn'?t|won'?t|showing|shows|broken|leaking)|not (fixed|working|solved)|didn'?t (work|help|fix)|same (problem|issue|error)|stopped working again""")
    private val FIXED = Regex("""\b(fixed|sorted|solved|resolved|all good)\b|(that|it) worked|(working|works|runs|running) (fine|now|again|great|perfectly|well)|good as new""")
    private val YES = Regex("""^\s*(yes|yeah|yep|yup|ya|haan)\b""")
    private val NO = Regex("""^\s*(no|nope|nah|not really)\b""")

    private val BRANDS = listOf(
        "Ariston", "LG", "Samsung", "Whirlpool", "IFB", "Bosch", "Haier", "Godrej", "Panasonic", "Voltas",
        "Heran", "Maruti", "Suzuki", "Hyundai", "Tata", "Honda", "Toyota", "Mahindra", "Kia", "Chevrolet",
        "Daikin", "Blue Star", "Lloyd", "Carrier", "Hitachi", "Sony", "Xiaomi", "TCL", "OnePlus", "Kent", "Aquaguard",
        "Livpure", "Pureit", "Racold", "AO Smith", "V-Guard", "Havells", "Crompton", "Bajaj", "Luminous", "Microtek",
        "Exide", "Livguard", "Hero", "TVS", "Royal Enfield", "Yamaha", "Dell", "Lenovo", "Asus", "Acer",
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
            ?: APPLIANCES.firstOrNull { it.words.containsMatchIn(q) }?.name
            ?: APPLIANCES.firstOrNull { it.words.containsMatchIn(answer.lowercase()) }?.name
        val brand = brandIn(q) ?: memory.brand
            ?: BRANDS.firstOrNull { Regex("""\b$it\b""").containsMatchIn(answer) }
        val symptoms = (memory.symptoms + SYMPTOMS.filter { it.second.containsMatchIn(q) }.map { it.first })
            .distinct().take(MAX_SYMPTOMS)
        return memory.copy(
            appliance = appliance,
            brand = brand,
            errorCode = errorCode(question) ?: memory.errorCode,
            symptoms = symptoms,
            outcome = outcome(question) ?: memory.outcome,
        )
    }

    /**
     * Notes from the reply to a greeting that asked after [earlier] ("yes, the car's fine now"). That reply is
     * about the earlier repair, so its device, brand, code and outcome aren't this session's; only something new
     * ("…and now my laptop won't start") is kept. Fixy's answer is ignored, since it talks about the old repair too.
     */
    fun afterFollowUp(memory: SessionMemory, reply: String, earlier: SessionMemory?): SessionMemory {
        val m = update(memory, reply, "")
        return memory.copy(
            appliance = m.appliance.takeIf { it != earlier?.appliance } ?: memory.appliance,
            brand = m.brand.takeIf { it != earlier?.brand } ?: memory.brand,
            errorCode = m.errorCode.takeIf { it != earlier?.errorCode } ?: memory.errorCode,
            symptoms = (memory.symptoms + (m.symptoms - earlier?.symptoms.orEmpty().toSet())).distinct().take(MAX_SYMPTOMS),
        )
    }

    /** True when the session's first turn only answered the greeting's "is it working now?" about an earlier repair. */
    fun openedWithFollowUp(session: RepairSession): Boolean =
        session.followUpOf != null && session.turns.firstOrNull()?.let { followUpOutcome(it.question) } != null

    /** "It works now" / "still not draining" in the user's words; null when they say neither. */
    fun outcome(text: String): Outcome? {
        val t = text.lowercase()
        return when {
            NOT_FIXED.containsMatchIn(t) -> Outcome.NotFixed
            FIXED.containsMatchIn(t) -> Outcome.Fixed
            else -> null
        }
    }

    /** The reply to the greeting's "is it working fine now?": a plain yes/no counts too. */
    fun followUpOutcome(reply: String): Outcome? {
        val t = reply.lowercase()
        return outcome(t) ?: when {
            NO.containsMatchIn(t) -> Outcome.NotFixed
            YES.containsMatchIn(t) -> Outcome.Fixed
            else -> null
        }
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
