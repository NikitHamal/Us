package com.us.copilot.ai.offline

import com.us.copilot.ai.LlmProvider
import com.us.copilot.ai.model.PatternReport
import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.RiskLevel
import com.us.copilot.ai.model.Theme
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.model.ProviderId
import com.us.copilot.core.util.Outcome
import com.us.copilot.core.util.TextUtils
import com.us.copilot.core.util.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.absoluteValue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully on-device provider. Nothing here touches the network, ever.
 *
 * It is a wrapper: rule engines answer today, and [CactModelLoader] lets a real `.cact` bundle
 * raise the confidence score (and later serve generative answers) without changing callers.
 */
@Singleton
class OfflineProvider @Inject constructor(
    private val modelLoader: CactModelLoader,
) : LlmProvider {

    override val id: ProviderId = ProviderId.OFFLINE

    override suspend fun isAvailable(): Boolean = true

    override suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis> =
        withContext(Dispatchers.Default) {
            val text = request.text.trim()
            val sentiment = Lexicon.sentimentScore(text)
            val horsemen = HorsemanDetector.detect(text)
            val triggerHits = TextUtils.matches(text, request.partner.triggers.map { it.lowercase() })
            val softeners = TextUtils.matches(text, Lexicon.softeners)

            val harshness = harshness(sentiment, horsemen.size, triggerHits.size, softeners.size, text)
            val risk = when {
                harshness >= 65 || horsemen.any { it.horseman.name == "CONTEMPT" } -> RiskLevel.HIGH
                harshness >= 35 || triggerHits.isNotEmpty() -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

            ToneAnalysis(
                sentiment = sentiment,
                primaryEmotion = Lexicon.detectEmotion(text),
                harshnessScore = harshness,
                detectedHorsemen = horsemen,
                triggerHits = triggerHits,
                summary = summary(risk, horsemen.size, triggerHits, request.partner.name),
                riskLevel = risk,
                confidence = confidence(text, sentiment, horsemen.isNotEmpty()) + modelLoader.confidenceBonus(),
                provider = ProviderId.OFFLINE,
            ).asSuccess()
        }

    override suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        withContext(Dispatchers.Default) {
            val nvc = NvcRephraser.buildNvc(request.text)
            RephraseSet(
                original = request.text,
                options = NvcRephraser.options(request.text, nvc, request.partner, request.styles),
                loveLanguageTip = NvcRephraser.loveLanguageTip(request.partner),
                nvc = nvc,
                confidence = (0.55f + modelLoader.confidenceBonus()).coerceAtMost(1f),
                provider = ProviderId.OFFLINE,
            ).asSuccess()
        }

    override suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        withContext(Dispatchers.Default) {
            if (request.entries.isEmpty()) {
                return@withContext PatternReport(confidence = 1f).asSuccess()
            }
            val themes = themes(request)
            val unresolved = request.entries.count { it.isUnresolved }
            val conflicts = request.entries.count { HorsemanDetector.isConflict(it.text) }
            val repairs = request.entries.count { HorsemanDetector.isRepairAttempt(it.text) }

            val observations = buildList {
                if (conflicts > 0) {
                    add("$conflicts of your last ${request.entries.size} moments carried conflict language.")
                }
                if (repairs > 0) {
                    add("You logged $repairs repair attempts — that is the single strongest predictor of staying together.")
                } else if (conflicts > 2) {
                    add("No repair attempts are showing up. A repair can be as small as \"that came out wrong\".")
                }
                if (unresolved > 0) add("$unresolved moments are still marked unresolved.")
                themes.firstOrNull()?.let {
                    add("\"${it.label}\" keeps coming back — it appeared ${it.occurrences} times.")
                }
            }

            val suggestions = buildList {
                themes.firstOrNull()?.let {
                    add("Pick a calm evening and talk about \"${it.label}\" once, properly, before it comes up hot again.")
                }
                if (unresolved > 0) add("Close one unresolved moment this week. Start with the oldest.")
                if (request.partner.soothers.isNotEmpty()) {
                    add("When it gets tense, reach for what settles her: ${request.partner.soothers.take(2).joinToString(", ")}.")
                }
                if (isEmpty()) add("Keep logging. Patterns need about two weeks of moments before they are honest.")
            }

            PatternReport(
                recurringThemes = themes,
                observations = observations,
                suggestions = suggestions,
                confidence = (0.6f + modelLoader.confidenceBonus()).coerceAtMost(1f),
                provider = ProviderId.OFFLINE,
            ).asSuccess()
        }

    /** Deterministic hashed bag-of-words embedding — private, fast, good enough for search. */
    override suspend fun embed(text: String): Outcome<FloatArray> = withContext(Dispatchers.Default) {
        val vector = FloatArray(EMBEDDING_DIM)
        TextUtils.contentTokens(text).forEach { token ->
            val bucket = (token.hashCode().absoluteValue) % EMBEDDING_DIM
            vector[bucket] += 1f
        }
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in vector.indices) vector[i] = vector[i] / norm
        vector.asSuccess()
    }

    private fun themes(request: PatternRequest): List<Theme> {
        val counts = mutableMapOf<String, MutableList<Long>>()
        request.entries.forEach { entry ->
            TextUtils.contentTokens(entry.text).distinct().forEach { token ->
                if (token.length >= 4) counts.getOrPut(token) { mutableListOf() }.add(entry.timestamp)
            }
        }
        return counts.filter { it.value.size >= 2 }
            .entries.sortedByDescending { it.value.size }
            .take(6)
            .map { (word, stamps) ->
                Theme(
                    label = word,
                    occurrences = stamps.size,
                    lastSeen = stamps.max(),
                    note = "Mentioned in ${stamps.size} moments.",
                )
            }
    }

    private fun harshness(
        sentiment: Float,
        horsemen: Int,
        triggers: Int,
        softeners: Int,
        text: String,
    ): Int {
        var score = ((-sentiment).coerceAtLeast(0f) * 55f)
        score += horsemen * 18f
        score += triggers * 12f
        score -= softeners * 8f
        if (text == text.uppercase() && text.length > 8) score += 15f
        if (text.count { it == '!' } >= 2) score += 8f
        return score.toInt().coerceIn(0, 100)
    }

    private fun summary(
        risk: RiskLevel,
        horsemen: Int,
        triggers: List<String>,
        partnerName: String,
    ): String {
        val her = partnerName.ifBlank { "she" }
        return when (risk) {
            RiskLevel.LOW -> "This reads as calm and clear. Safe to send."
            RiskLevel.MEDIUM -> buildString {
                append("This mostly lands okay, but there is an edge in it. ")
                if (triggers.isNotEmpty()) append("It touches a known trigger: ${triggers.first()}. ")
                append("A softer opening would help.")
            }
            RiskLevel.HIGH -> buildString {
                append("Sending this will most likely start a fight. ")
                if (horsemen > 0) append("It contains $horsemen of the Four Horsemen. ")
                append("Try the rewrite below, or wait twenty minutes before replying to $her.")
            }
        }
    }

    private fun confidence(text: String, sentiment: Float, horsemenFound: Boolean): Float {
        val length = text.trim().length
        val base = when {
            length < 12 -> 0.35f
            length < 40 -> 0.5f
            else -> 0.62f
        }
        val signal = if (horsemenFound || abs(sentiment) > 0.4f) 0.12f else 0f
        return (base + signal).coerceIn(0f, 1f)
    }

    private companion object { const val EMBEDDING_DIM = 128 }
}
