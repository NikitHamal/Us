package com.us.copilot.ai.offline

import com.us.copilot.core.model.Emotion

/**
 * Hand-curated, on-device lexicon. Deliberately small and readable: it is the fallback that
 * guarantees the app is useful with zero network and zero model file.
 */
object Lexicon {

    val positive = setOf(
        "love", "thank", "thanks", "grateful", "proud", "happy", "safe", "warm", "beautiful",
        "sweet", "miss", "appreciate", "sorry", "understand", "together", "care", "kind",
        "excited", "calm", "gentle", "hug", "smile", "best", "amazing", "lucky",
    )

    val negative = setOf(
        "hate", "never", "always", "stupid", "ridiculous", "annoying", "whatever", "fine",
        "tired", "done", "useless", "selfish", "lazy", "liar", "ignore", "ignored", "angry",
        "mad", "upset", "hurt", "unfair", "blame", "fault", "wrong", "again", "forget",
        "pathetic", "immature", "dramatic", "overreacting", "toxic",
    )

    val intensifiers = setOf("very", "so", "really", "totally", "completely", "absolutely", "literally")

    val emotionCues: Map<Emotion, Set<String>> = mapOf(
        Emotion.ANGER to setOf("angry", "furious", "mad", "rage", "pissed", "fed up", "sick of"),
        Emotion.HURT to setOf("hurt", "wounded", "betrayed", "let down", "disrespected", "used"),
        Emotion.SADNESS to setOf("sad", "crying", "cry", "depressed", "down", "empty", "miserable"),
        Emotion.ANXIETY to setOf("anxious", "worried", "scared", "afraid", "panic", "nervous", "overthinking"),
        Emotion.LONELINESS to setOf("alone", "lonely", "distant", "ignored", "invisible", "abandoned"),
        Emotion.SHAME to setOf("ashamed", "embarrassed", "guilty", "my fault", "stupid of me"),
        Emotion.LOVE to setOf("love you", "adore", "my everything", "obsessed with you", "mine"),
        Emotion.JOY to setOf("happy", "excited", "laughing", "great news", "amazing", "yay"),
        Emotion.GRATITUDE to setOf("thank you", "grateful", "appreciate", "means a lot"),
        Emotion.CALM to setOf("okay now", "peaceful", "settled", "relaxed", "better now"),
    )

    /** Phrases that show someone is trying to de-escalate — Gottman's repair attempts. */
    val repairAttempts = setOf(
        "i'm sorry", "im sorry", "i am sorry", "my bad", "you're right", "youre right",
        "i hear you", "can we start over", "let me try again", "i love you", "i don't want to fight",
        "i dont want to fight", "can we talk", "i need a break", "that came out wrong",
        "i shouldn't have", "i shouldnt have", "forgive me", "truce", "i miss us",
    )

    /** Phrases signalling escalation / conflict. */
    val conflictMarkers = setOf(
        "you always", "you never", "whatever", "forget it", "i'm done", "im done",
        "leave me alone", "shut up", "stop talking", "not again", "typical", "as usual",
        "i can't with you", "i cant with you", "block", "breaking up", "break up",
    )

    /** Softening words that lower perceived harshness. */
    val softeners = setOf(
        "please", "maybe", "could we", "would you", "i feel", "i felt", "i need",
        "help me understand", "when you have time", "thank you", "i appreciate",
    )

    fun sentimentScore(text: String): Float {
        val lower = text.lowercase()
        val words = Regex("[a-z']+").findAll(lower).map { it.value }.toList()
        if (words.isEmpty()) return 0f
        var score = 0f
        words.forEachIndexed { index, word ->
            val weight = if (index > 0 && words[index - 1] in intensifiers) 1.5f else 1f
            when (word) {
                in positive -> score += weight
                in negative -> score -= weight
            }
        }
        conflictMarkers.forEach { if (lower.contains(it)) score -= 2f }
        repairAttempts.forEach { if (lower.contains(it)) score += 2f }
        val magnitude = words.size.coerceAtLeast(6).toFloat()
        return (score / magnitude * 3f).coerceIn(-1f, 1f)
    }

    fun detectEmotion(text: String): Emotion {
        val lower = text.lowercase()
        val hit = emotionCues.entries
            .map { (emotion, cues) -> emotion to cues.count { lower.contains(it) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
        if (hit != null) return hit.first
        val sentiment = sentimentScore(text)
        return when {
            sentiment > 0.25f -> Emotion.JOY
            sentiment < -0.25f -> Emotion.ANGER
            else -> Emotion.NEUTRAL
        }
    }
}
