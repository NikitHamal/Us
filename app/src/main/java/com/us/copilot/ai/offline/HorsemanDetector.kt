package com.us.copilot.ai.offline

import com.us.copilot.ai.model.HorsemanHit
import com.us.copilot.core.model.Horseman

/**
 * Rule-based detector for Gottman's Four Horsemen. Each rule stores the evidence phrase so the UI
 * can show the user exactly which words triggered the flag — no black boxes.
 */
object HorsemanDetector {

    private val criticism = listOf(
        "you always", "you never", "why can't you", "why cant you", "what's wrong with you",
        "whats wrong with you", "you're the kind of person", "youre the kind of person",
        "you don't care", "you dont care", "you're so", "youre so",
    )

    private val contempt = listOf(
        "pathetic", "ridiculous", "grow up", "you're a joke", "youre a joke", "obviously",
        "as if you", "wow, impressive", "sure, genius", "typical of you", "you're immature",
        "youre immature", "get over yourself", "eye roll", "lol ok", "clown",
    )

    private val defensiveness = listOf(
        "it's not my fault", "its not my fault", "i didn't do anything", "i didnt do anything",
        "what about you", "you did it too", "i only did that because", "you're the one who",
        "youre the one who", "well you", "stop blaming me", "i never said that",
    )

    private val stonewalling = listOf(
        "whatever", "forget it", "i'm done talking", "im done talking", "nothing", "k.",
        "leave me alone", "i don't want to talk", "i dont want to talk", "no comment",
        "not discussing this", "bye.",
    )

    fun detect(text: String): List<HorsemanHit> {
        val lower = text.lowercase().trim()
        val hits = mutableListOf<HorsemanHit>()
        firstMatch(lower, criticism)?.let { hits += HorsemanHit(Horseman.CRITICISM, it) }
        firstMatch(lower, contempt)?.let { hits += HorsemanHit(Horseman.CONTEMPT, it) }
        firstMatch(lower, defensiveness)?.let { hits += HorsemanHit(Horseman.DEFENSIVENESS, it) }
        firstMatch(lower, stonewalling)?.let { hits += HorsemanHit(Horseman.STONEWALLING, it) }

        // A one-word dismissive reply is stonewalling even without a keyword match.
        if (hits.none { it.horseman == Horseman.STONEWALLING } &&
            lower.length <= 4 && lower in setOf("k", "ok", "fine", "sure", "cool")
        ) {
            hits += HorsemanHit(Horseman.STONEWALLING, lower)
        }
        return hits
    }

    fun isRepairAttempt(text: String): Boolean {
        val lower = text.lowercase()
        return Lexicon.repairAttempts.any { lower.contains(it) }
    }

    fun isConflict(text: String): Boolean {
        val lower = text.lowercase()
        return Lexicon.conflictMarkers.any { lower.contains(it) } || detect(text).isNotEmpty()
    }

    private fun firstMatch(lower: String, phrases: List<String>): String? =
        phrases.firstOrNull { lower.contains(it) }
}
