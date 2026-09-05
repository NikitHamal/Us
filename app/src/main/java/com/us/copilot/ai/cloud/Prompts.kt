package com.us.copilot.ai.cloud

import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.ai.model.ProfileContext
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.ToneRequest

/** All prompt text lives here so it can be reviewed and tuned in one place. */
object Prompts {

    private const val ETHICS = """
You are a relationship communication coach grounded in Nonviolent Communication (Rosenberg),
Gottman Institute research, and attachment theory. You help ONE person express themselves honestly
and kindly to their partner.
Hard rules:
- Never help manipulate, guilt-trip, coerce, surveil, or "win" against the partner.
- Never assume the partner's motives; speak only about observable behaviour and the user's feelings.
- Never suggest deception. Never encourage contact if abuse is described; instead suggest support.
- Respond with valid JSON only. No markdown, no commentary outside the JSON object.
"""

    fun toneSystem(): String = ETHICS.trimIndent() + """

Return JSON with exactly this shape:
{"sentiment": -1.0..1.0, "emotion": one of
 JOY|LOVE|CALM|GRATITUDE|NEUTRAL|ANXIETY|SADNESS|ANGER|HURT|LONELINESS|SHAME,
 "harshness": 0..100, "horsemen": [{"type":"CRITICISM|CONTEMPT|DEFENSIVENESS|STONEWALLING",
 "evidence":"the exact words"}], "triggers": ["matched partner trigger"],
 "summary": "two sentences, plain and warm", "risk": "LOW|MEDIUM|HIGH", "confidence": 0.0..1.0}
"""

    fun toneUser(request: ToneRequest): String = buildString {
        appendLine("Message author: ${if (request.authorIsMe) "the user" else "the partner"}")
        appendLine(profileBlock("Partner", request.partner))
        appendLine(profileBlock("User", request.me))
        appendLine("Message:")
        appendLine("\"\"\"")
        appendLine(request.text.take(4000))
        append("\"\"\"")
    }

    fun rephraseSystem(): String = ETHICS.trimIndent() + """

Rewrite the user's message three ways using NVC (Observation, Feeling, Need, Request).
Keep the user's real meaning; do not water down the truth, only remove blame.
Return JSON with exactly this shape:
{"soft":"", "soft_why":"", "direct":"", "direct_why":"", "playful":"", "playful_why":"",
 "love_language":"WORDS|QUALITY_TIME|ACTS|GIFTS|TOUCH", "love_language_tip":"",
 "observation":"", "feeling":"", "need":"", "request":"", "confidence": 0.0..1.0}
"""

    fun rephraseUser(request: RephraseRequest): String = buildString {
        appendLine(profileBlock("Partner", request.partner))
        appendLine(profileBlock("User", request.me))
        appendLine("Rewrite this message:")
        appendLine("\"\"\"")
        appendLine(request.text.take(4000))
        append("\"\"\"")
    }

    fun patternSystem(): String = ETHICS.trimIndent() + """

You are given a chronological list of relationship moments the user logged themselves.
Find honest patterns. Be specific and kind, and hold the user accountable too, not only the partner.
Return JSON with exactly this shape:
{"themes":[{"label":"","occurrences":1,"note":""}], "observations":["..."],
 "suggestions":["concrete, doable this week"], "confidence": 0.0..1.0}
"""

    fun patternUser(request: PatternRequest): String = buildString {
        appendLine(profileBlock("Partner", request.partner))
        appendLine("Moments (oldest first):")
        request.entries.takeLast(120).forEach { entry ->
            appendLine(
                "- [${entry.timestamp}] (${entry.speaker}, ${entry.emotion}" +
                    (if (entry.isUnresolved) ", unresolved" else "") + ") ${entry.text.take(400)}",
            )
        }
    }

    private fun profileBlock(label: String, context: ProfileContext): String {
        if (context.isEmpty) return "$label profile: not provided."
        return buildString {
            appendLine("$label profile:")
            if (context.name.isNotBlank()) appendLine("  name: ${context.name}")
            if (context.attachmentStyle.isNotBlank()) appendLine("  attachment: ${context.attachmentStyle}")
            if (context.loveLanguages.isNotEmpty()) {
                appendLine("  love languages (ranked): ${context.loveLanguages.joinToString(", ")}")
            }
            if (context.conflictStyle.isNotBlank()) appendLine("  conflict style: ${context.conflictStyle}")
            if (context.triggers.isNotEmpty()) appendLine("  triggers: ${context.triggers.joinToString(", ")}")
            if (context.soothers.isNotEmpty()) appendLine("  soothers: ${context.soothers.joinToString(", ")}")
            if (context.commPreferences.isNotEmpty()) {
                append("  communication preferences: ${context.commPreferences.joinToString(", ")}")
            }
        }
    }
}
