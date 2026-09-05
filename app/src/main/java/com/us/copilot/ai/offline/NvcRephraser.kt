package com.us.copilot.ai.offline

import com.us.copilot.ai.model.LoveLanguageTip
import com.us.copilot.ai.model.NvcBreakdown
import com.us.copilot.ai.model.ProfileContext
import com.us.copilot.ai.model.RephraseOption
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.RephraseStyle
import com.us.copilot.core.util.TextUtils

/**
 * Turns a raw, often hot, message into Nonviolent Communication:
 * Observation → Feeling → Need → Request, then renders three voices from it.
 */
object NvcRephraser {

    private val youAccusations = mapOf(
        "you always" to "it has happened a few times that",
        "you never" to "it is rare that",
        "you don't" to "I do not always feel that you",
        "you dont" to "I do not always feel that you",
        "you make me" to "I end up feeling",
        "you're" to "it feels like you are",
        "youre" to "it feels like you are",
    )

    private val feelingByEmotionWord = mapOf(
        "ignored" to "unseen", "alone" to "lonely", "angry" to "frustrated",
        "mad" to "frustrated", "annoying" to "overwhelmed", "hurt" to "hurt",
        "sad" to "low", "worried" to "anxious", "scared" to "anxious", "tired" to "worn out",
    )

    private val needByFeeling = mapOf(
        "unseen" to "to feel noticed by you",
        "lonely" to "more closeness with you",
        "frustrated" to "us to work as a team on this",
        "hurt" to "some care and reassurance",
        "low" to "a little comfort",
        "anxious" to "some reassurance that we are okay",
        "worn out" to "a bit of support and rest",
        "unsettled" to "clarity between us",
    )

    fun buildNvc(text: String): NvcBreakdown {
        val clean = text.trim().ifBlank { "something happened" }
        val observation = observation(clean)
        val feeling = feeling(clean)
        val need = needByFeeling[feeling] ?: "us to understand each other here"
        return NvcBreakdown(
            observation = "When $observation,",
            feeling = "I feel $feeling,",
            need = "because I need $need.",
            request = "Would you be open to talking about it with me?",
        )
    }

    fun options(
        text: String,
        nvc: NvcBreakdown,
        partner: ProfileContext,
        styles: List<RephraseStyle>,
    ): List<RephraseOption> = styles.map { style ->
        when (style) {
            RephraseStyle.SOFT -> RephraseOption(
                style = style,
                text = softVoice(nvc, partner),
                why = "Starts gently so she does not have to defend herself in the first sentence.",
            )
            RephraseStyle.DIRECT -> RephraseOption(
                style = style,
                text = directVoice(nvc),
                why = "Says the whole truth without a single blaming word.",
            )
            RephraseStyle.PLAYFUL -> RephraseOption(
                style = style,
                text = playfulVoice(nvc, text),
                why = "Lowers the temperature with warmth, best for small friction, not big wounds.",
            )
        }
    }

    fun loveLanguageTip(partner: ProfileContext): LoveLanguageTip? {
        val primary = partner.loveLanguages.firstOrNull()
            ?.let { name -> LoveLanguage.entries.firstOrNull { it.name == name } }
            ?: return null
        val suggestion = when (primary) {
            LoveLanguage.WORDS ->
                "Add one line of appreciation before the hard part: name something specific she did."
            LoveLanguage.QUALITY_TIME ->
                "Offer a time, not just words: \"can we take twenty minutes tonight, phones away?\""
            LoveLanguage.ACTS ->
                "Pair the message with one concrete thing you will handle for her today."
            LoveLanguage.GIFTS ->
                "A small token afterwards will say what the message cannot. Make it specific to her."
            LoveLanguage.TOUCH ->
                "If you are together, say this while sitting close. Distance reads as coldness to her."
        }
        return LoveLanguageTip(primary, suggestion)
    }

    private fun observation(text: String): String {
        var out = text.lowercase().trim().trimEnd('.', '!', '?')
        youAccusations.forEach { (accusation, neutral) -> out = out.replace(accusation, neutral) }
        out = out.replace(Regex("\\s+"), " ")
        val trimmed = if (out.length > 140) out.take(137) + "..." else out
        return trimmed.ifBlank { "this came up between us" }
    }

    private fun feeling(text: String): String {
        val lower = text.lowercase()
        feelingByEmotionWord.forEach { (cue, feeling) -> if (lower.contains(cue)) return feeling }
        val sentiment = Lexicon.sentimentScore(text)
        return when {
            sentiment < -0.5f -> "frustrated"
            sentiment < -0.15f -> "unsettled"
            else -> "unsettled"
        }
    }

    private fun softVoice(nvc: NvcBreakdown, partner: ProfileContext): String {
        val opener = if (partner.name.isNotBlank()) "${partner.name}, " else ""
        return TextUtils.titleCaseFirst(
            "${opener}I want to say something and I want to say it kindly. " +
                "${nvc.observation} ${nvc.feeling} ${nvc.need} ${nvc.request}",
        )
    }

    private fun directVoice(nvc: NvcBreakdown): String =
        TextUtils.titleCaseFirst("${nvc.observation} ${nvc.feeling} ${nvc.need} ${nvc.request}")

    private fun playfulVoice(nvc: NvcBreakdown, original: String): String {
        val need = nvc.need.removePrefix("because I need ").trimEnd('.')
        val topic = original.trim().trimEnd('.', '!', '?').take(60).lowercase()
        return "Okay, small confession: the \"$topic\" thing has been living in my head rent free. " +
            "Nothing dramatic, I just need $need. Can we sort it out over something warm to drink?"
    }
}
