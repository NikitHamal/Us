package com.us.copilot.core.model

/** One remembered moment: a shared message, a journal line, a note to self. */
data class Memory(
    val id: Long = 0L,
    val text: String,
    val emotion: Emotion = Emotion.NEUTRAL,
    val intensity: Int = 3,
    val timestamp: Long = 0L,
    val source: MemorySource = MemorySource.MANUAL,
    val speaker: Speaker = Speaker.BOTH,
    val tags: List<String> = emptyList(),
    val isUnresolved: Boolean = false,
    val resolvedAt: Long? = null,
    val embedding: FloatArray? = null,
    val appPackage: String? = null,
) {
    val preview: String get() = text.trim().take(160)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Memory) return false
        return id == other.id && text == other.text && timestamp == other.timestamp &&
            emotion == other.emotion && isUnresolved == other.isUnresolved &&
            tags == other.tags && intensity == other.intensity
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + emotion.hashCode()
        result = 31 * result + isUnresolved.hashCode()
        return result
    }
}

/** Filters applied to the timeline. */
data class MemoryFilter(
    val query: String = "",
    val emotions: Set<Emotion> = emptySet(),
    val sources: Set<MemorySource> = emptySet(),
    val speakers: Set<Speaker> = emptySet(),
    val onlyUnresolved: Boolean = false,
    val tag: String? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || emotions.isNotEmpty() || sources.isNotEmpty() ||
            speakers.isNotEmpty() || onlyUnresolved || tag != null
}

/** Suggested tags so the timeline stays searchable without free-form chaos. */
object MemoryTags {
    val suggested = listOf(
        "argument", "repair", "misunderstanding", "distance", "jealousy", "plans",
        "family", "money", "future", "affection", "win", "apology", "boundary", "sex",
        "work stress", "long distance",
    )
}
