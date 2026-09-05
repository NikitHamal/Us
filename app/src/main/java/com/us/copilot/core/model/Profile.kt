package com.us.copilot.core.model

/** Big Five (OCEAN) scores, each 0..100. */
data class BigFive(
    val openness: Int = 50,
    val conscientiousness: Int = 50,
    val extraversion: Int = 50,
    val agreeableness: Int = 50,
    val neuroticism: Int = 50,
) {
    val asPairs: List<Pair<String, Int>>
        get() = listOf(
            "Openness" to openness,
            "Conscientiousness" to conscientiousness,
            "Extraversion" to extraversion,
            "Agreeableness" to agreeableness,
            "Emotional reactivity" to neuroticism,
        )

    companion object {
        val Neutral = BigFive()
    }
}

/**
 * A structured psychological profile. Never free text at the top level:
 * every field is an enum, a bounded number, or a curated tag list.
 */
data class Profile(
    val id: Long = 0L,
    val owner: ProfileOwner,
    val name: String,
    val attachmentStyle: AttachmentStyle = AttachmentStyle.UNKNOWN,
    /** Ranked, most important first. */
    val loveLanguages: List<LoveLanguage> = emptyList(),
    val conflictStyle: ConflictStyle = ConflictStyle.UNKNOWN,
    val triggers: List<String> = emptyList(),
    val soothers: List<String> = emptyList(),
    val bigFive: BigFive = BigFive.Neutral,
    val stressPatterns: List<String> = emptyList(),
    val commPreferences: List<String> = emptyList(),
    val note: String = "",
    val version: Int = 1,
    val isActive: Boolean = true,
    val updatedAt: Long = 0L,
) {
    val primaryLoveLanguage: LoveLanguage? get() = loveLanguages.firstOrNull()

    val completeness: Float
        get() {
            val checks = listOf(
                name.isNotBlank(),
                attachmentStyle != AttachmentStyle.UNKNOWN,
                loveLanguages.isNotEmpty(),
                conflictStyle != ConflictStyle.UNKNOWN,
                triggers.isNotEmpty(),
                soothers.isNotEmpty(),
                stressPatterns.isNotEmpty(),
                commPreferences.isNotEmpty(),
            )
            return checks.count { it } / checks.size.toFloat()
        }

    companion object {
        fun empty(owner: ProfileOwner, name: String = "") = Profile(owner = owner, name = name)
    }
}

/** Curated suggestion banks so users tap instead of typing prose. */
object ProfileVocabulary {
    val triggers = listOf(
        "Being interrupted", "Feeling dismissed", "Raised voices", "Silence after a fight",
        "Broken plans", "Being compared to someone", "Feeling controlled", "Late replies",
        "Being told to calm down", "Money talk", "Family criticism", "Feeling unappreciated",
    )
    val soothers = listOf(
        "A sincere apology", "Being held", "Time alone first", "A clear plan",
        "Hearing \"you matter to me\"", "Food and rest", "Humour", "A walk together",
        "Being asked what I need", "Reassurance we are okay",
    )
    val stressPatterns = listOf(
        "Goes quiet", "Talks faster", "Overworks", "Sleeps badly", "Cancels plans",
        "Snaps at small things", "Scrolls for hours", "Needs constant contact",
        "Cleans obsessively", "Cries easily",
    )
    val commPreferences = listOf(
        "Voice notes over text", "Never fight over text", "Say it in person",
        "Give me a heads up before hard talks", "Short messages, not essays",
        "Reply even if just \"busy, later\"", "No sarcasm", "Ask before advice",
    )
}
