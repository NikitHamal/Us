package com.us.copilot.ai.nebians

import com.us.copilot.ai.LlmProvider
import com.us.copilot.ai.cloud.Prompts
import com.us.copilot.ai.model.HorsemanHit
import com.us.copilot.ai.model.LoveLanguageTip
import com.us.copilot.ai.model.NvcBreakdown
import com.us.copilot.ai.model.PatternReport
import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.ai.model.RephraseOption
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.RiskLevel
import com.us.copilot.ai.model.Theme
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Horseman
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.ProviderId
import com.us.copilot.core.model.RephraseStyle
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.domain.repository.SettingsRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nebians fleet as a first-class [LlmProvider].
 *
 * Single-shot structured tasks (tone, rephrase, patterns) run as one chat
 * turn against the user's selected Nebians provider + model, then parse the
 * JSON exactly like the OpenAI-compatible cloud path. Free guest providers
 * need no key, so enabling cloud AI is enough to use them; key-required
 * providers additionally need a key in Settings.
 *
 * Embeddings are not offered by any Nebians endpoint — that call always
 * fails so the router keeps the on-device vector.
 */
@Singleton
class NebiansProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val dispatcher: NebiansDispatcher,
) : LlmProvider {

    override val id: ProviderId = ProviderId.NEBIANS

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    override suspend fun isAvailable(): Boolean {
        val config = settings.nebiansConfigSnapshot()
        val provider = NebiansCatalog.find(config.providerSlug) ?: return false
        if (provider.slug == "custom") return config.baseUrlOverride.isNotBlank()
        if (provider.keyRequired) return config.apiKey.isNotBlank()
        return true
    }

    override suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis> =
        chat(Prompts.toneSystem(), Prompts.toneUser(request)) { raw ->
            val dto = json.decodeFromString(
                com.us.copilot.ai.cloud.ToneDto.serializer(),
                raw,
            )
            ToneAnalysis(
                sentiment = dto.sentiment.coerceIn(-1f, 1f),
                primaryEmotion = enumOr(dto.emotion, Emotion.NEUTRAL),
                harshnessScore = dto.harshness.coerceIn(0, 100),
                detectedHorsemen = dto.horsemen.mapNotNull { hit ->
                    Horseman.entries.firstOrNull { it.name.equals(hit.type, true) }
                        ?.let { HorsemanHit(it, hit.evidence) }
                },
                triggerHits = dto.triggers,
                summary = dto.summary.ifBlank { "Analysis complete." },
                riskLevel = enumOr(dto.risk, RiskLevel.LOW),
                confidence = dto.confidence.coerceIn(0f, 1f),
                provider = ProviderId.NEBIANS,
            )
        }

    override suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        chat(Prompts.rephraseSystem(), Prompts.rephraseUser(request)) { raw ->
            val dto = json.decodeFromString(
                com.us.copilot.ai.cloud.RephraseDto.serializer(),
                raw,
            )
            val options = buildList {
                if (dto.soft.isNotBlank()) add(RephraseOption(RephraseStyle.SOFT, dto.soft, dto.softWhy))
                if (dto.direct.isNotBlank()) add(RephraseOption(RephraseStyle.DIRECT, dto.direct, dto.directWhy))
                if (dto.playful.isNotBlank()) add(RephraseOption(RephraseStyle.PLAYFUL, dto.playful, dto.playfulWhy))
            }
            RephraseSet(
                original = request.text,
                options = options,
                loveLanguageTip = LoveLanguage.entries
                    .firstOrNull { it.name.equals(dto.loveLanguage, true) }
                    ?.let { LoveLanguageTip(it, dto.loveLanguageTip) },
                nvc = if (dto.feeling.isBlank()) null else {
                    NvcBreakdown(dto.observation, dto.feeling, dto.need, dto.request)
                },
                confidence = dto.confidence.coerceIn(0f, 1f),
                provider = ProviderId.NEBIANS,
            )
        }

    override suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        chat(Prompts.patternSystem(), Prompts.patternUser(request)) { raw ->
            val dto = json.decodeFromString(
                com.us.copilot.ai.cloud.PatternDto.serializer(),
                raw,
            )
            PatternReport(
                recurringThemes = dto.themes.map {
                    Theme(it.label, it.occurrences, System.currentTimeMillis(), it.note)
                },
                observations = dto.observations,
                suggestions = dto.suggestions,
                confidence = dto.confidence.coerceIn(0f, 1f),
                provider = ProviderId.NEBIANS,
            )
        }

    override suspend fun embed(text: String): Outcome<FloatArray> =
        Outcome.Failure(AppError.Unknown("Nebians providers do not offer embeddings."))

    private suspend fun <T> chat(
        system: String,
        user: String,
        parse: (String) -> T,
    ): Outcome<T> {
        val config = settings.nebiansConfigSnapshot()
        if (NebiansCatalog.find(config.providerSlug) == null) {
            return Outcome.Failure(AppError.MissingCredentials)
        }
        return try {
            val result = dispatcher.ask(config, system, user)
            runCatching { Outcome.Success(parse(extractJson(result.text))) }
                .getOrElse { Outcome.Failure(AppError.Parse(it.message ?: "malformed JSON")) }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (e: NebiansException) {
            Outcome.Failure(AppError.Unknown(e.message ?: "Nebians request failed"))
        } catch (io: java.io.IOException) {
            Outcome.Failure(AppError.NoNetwork)
        } catch (t: Throwable) {
            Outcome.Failure(AppError.Unknown(t.message ?: "Nebians request failed"))
        }
    }

    /** Tolerates models that wrap JSON in prose or code fences. */
    private fun extractJson(raw: String): String {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name.equals(name, true) } ?: fallback
}
