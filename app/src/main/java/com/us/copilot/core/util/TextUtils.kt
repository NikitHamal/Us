package com.us.copilot.core.util

import java.security.MessageDigest
import kotlin.math.sqrt

/** Small, dependency-free text helpers shared by the AI and pattern layers. */
object TextUtils {

    private val tokenRegex = Regex("[a-z']+")

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "if", "of", "to", "in", "on", "for", "with",
        "is", "are", "was", "were", "be", "been", "am", "it", "this", "that", "these", "those",
        "i", "me", "my", "you", "your", "we", "us", "our", "she", "her", "he", "him", "they",
        "so", "just", "very", "really", "at", "as", "by", "from", "do", "did", "does", "not",
    )

    fun tokens(text: String): List<String> =
        tokenRegex.findAll(text.lowercase()).map { it.value }.filter { it.length > 1 }.toList()

    fun contentTokens(text: String): List<String> = tokens(text).filterNot { it in stopWords }

    fun containsAny(text: String, phrases: Collection<String>): Boolean {
        val lower = text.lowercase()
        return phrases.any { lower.contains(it) }
    }

    fun matches(text: String, phrases: Collection<String>): List<String> {
        val lower = text.lowercase()
        return phrases.filter { lower.contains(it) }
    }

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }

    /** Strips the "Sent from Instagram"-style noise share intents often carry. */
    fun cleanSharedText(raw: String): String = raw
        .lineSequence()
        .map { it.trim() }
        .filterNot { it.startsWith("http", ignoreCase = true) && it.length > 60 }
        .filterNot { it.matches(Regex("(?i)^(sent (from|via)|shared (from|via)).*")) }
        .joinToString("\n")
        .trim()

    fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

    fun titleCaseFirst(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
