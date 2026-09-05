package com.us.copilot.ai.model

import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Horseman
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.ProviderId
import com.us.copilot.core.model.RephraseStyle
import kotlinx.serialization.Serializable

/** Minimal, serialisable snapshot of a profile handed to a provider. */
@Serializable
data class ProfileContext(
    val name: String = "",
    val attachmentStyle: String = "",
    val loveLanguages: List<String> = emptyList(),
    val conflictStyle: String = "",
    val triggers: List<String> = emptyList(),
    val soothers: List<String> = emptyList(),
    val commPreferences: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = name.isBlank() && triggers.isEmpty() && loveLanguages.isEmpty()

    companion object { val Empty = ProfileContext() }
}

@Serializable
data class ToneRequest(
    val text: String,
    val authorIsMe: Boolean = true,
    val partner: ProfileContext = ProfileContext.Empty,
    val me: ProfileContext = ProfileContext.Empty,
)

@Serializable
data class ToneAnalysis(
    /** -1.0 hostile .. +1.0 warm. */
    val sentiment: Float,
    val primaryEmotion: Emotion,
    /** 0..100, how likely this lands as an attack. */
    val harshnessScore: Int,
    val detectedHorsemen: List<HorsemanHit> = emptyList(),
    val triggerHits: List<String> = emptyList(),
    val summary: String,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val confidence: Float = 0f,
    val provider: ProviderId = ProviderId.OFFLINE,
) {
    val isSafeToSend: Boolean get() = riskLevel == RiskLevel.LOW
}

@Serializable
data class HorsemanHit(val horseman: Horseman, val evidence: String)

enum class RiskLevel(val label: String) {
    LOW("Looks kind"), MEDIUM("Could sting"), HIGH("This will start a fight")
}

@Serializable
data class RephraseRequest(
    val text: String,
    val partner: ProfileContext = ProfileContext.Empty,
    val me: ProfileContext = ProfileContext.Empty,
    val styles: List<RephraseStyle> = RephraseStyle.entries.toList(),
)

@Serializable
data class RephraseOption(
    val style: RephraseStyle,
    val text: String,
    val why: String,
)

@Serializable
data class RephraseSet(
    val original: String,
    val options: List<RephraseOption>,
    val loveLanguageTip: LoveLanguageTip? = null,
    val nvc: NvcBreakdown? = null,
    val confidence: Float = 0f,
    val provider: ProviderId = ProviderId.OFFLINE,
)

@Serializable
data class LoveLanguageTip(val language: LoveLanguage, val suggestion: String)

/** Observation / Feeling / Need / Request. */
@Serializable
data class NvcBreakdown(
    val observation: String,
    val feeling: String,
    val need: String,
    val request: String,
) {
    fun asMessage(): String = "$observation $feeling $need $request".replace(Regex("\\s+"), " ").trim()
}

@Serializable
data class PatternRequest(
    val entries: List<PatternEntry>,
    val partner: ProfileContext = ProfileContext.Empty,
)

@Serializable
data class PatternEntry(
    val text: String,
    val timestamp: Long,
    val emotion: String,
    val speaker: String,
    val isUnresolved: Boolean,
)

@Serializable
data class PatternReport(
    val recurringThemes: List<Theme> = emptyList(),
    val observations: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val confidence: Float = 0f,
    val provider: ProviderId = ProviderId.OFFLINE,
) {
    val isEmpty: Boolean get() = recurringThemes.isEmpty() && observations.isEmpty()
}

@Serializable
data class Theme(val label: String, val occurrences: Int, val lastSeen: Long, val note: String = "")
