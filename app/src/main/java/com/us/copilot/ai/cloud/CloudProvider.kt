package com.us.copilot.ai.cloud

import com.us.copilot.ai.LlmProvider
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic OpenAI-compatible client. Everything that identifies the endpoint — base URL, API key,
 * model name — is read from encrypted settings at call time, so any proxy or self-hosted gateway
 * works without a rebuild. No credential is ever hardcoded or logged.
 */
@Singleton
class CloudProvider @Inject constructor(
    private val httpClient: HttpClient,
    private val settings: SettingsRepository,
) : LlmProvider {

    override val id: ProviderId = ProviderId.CLOUD

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    override suspend fun isAvailable(): Boolean = settings.cloudCredentials().isComplete

    override suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis> =
        chat(Prompts.toneSystem(), Prompts.toneUser(request)) { raw ->
            val dto = json.decodeFromString(ToneDto.serializer(), raw)
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
                provider = ProviderId.CLOUD,
            )
        }

    override suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        chat(Prompts.rephraseSystem(), Prompts.rephraseUser(request)) { raw ->
            val dto = json.decodeFromString(RephraseDto.serializer(), raw)
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
                provider = ProviderId.CLOUD,
            )
        }

    override suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        chat(Prompts.patternSystem(), Prompts.patternUser(request)) { raw ->
            val dto = json.decodeFromString(PatternDto.serializer(), raw)
            PatternReport(
                recurringThemes = dto.themes.map {
                    Theme(it.label, it.occurrences, System.currentTimeMillis(), it.note)
                },
                observations = dto.observations,
                suggestions = dto.suggestions,
                confidence = dto.confidence.coerceIn(0f, 1f),
                provider = ProviderId.CLOUD,
            )
        }

    override suspend fun embed(text: String): Outcome<FloatArray> {
        val creds = settings.cloudCredentials()
        if (!creds.isComplete) return Outcome.Failure(AppError.MissingCredentials)
        return safeCall {
            val response: HttpResponse = httpClient.post(creds.endpoint(EMBEDDINGS_PATH)) {
                authHeaders(creds.apiKey)
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(creds.embeddingModel.ifBlank { creds.modelName }, text.take(8000)))
            }
            if (!response.status.isSuccess()) {
                return@safeCall Outcome.Failure(AppError.Http(response.status.value, response.bodyAsText().take(300)))
            }
            val body: EmbeddingResponse = response.body()
            val vector = body.data.firstOrNull()?.embedding
                ?: return@safeCall Outcome.Failure(AppError.Parse("empty embedding payload"))
            Outcome.Success(vector.toFloatArray())
        }
    }

    private suspend fun <T> chat(
        system: String,
        user: String,
        parse: (String) -> T,
    ): Outcome<T> {
        val creds = settings.cloudCredentials()
        if (!creds.isComplete) return Outcome.Failure(AppError.MissingCredentials)

        return safeCall {
            val response: HttpResponse = httpClient.post(creds.endpoint(CHAT_PATH)) {
                authHeaders(creds.apiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = creds.modelName,
                        messages = listOf(
                            ChatMessage("system", system),
                            ChatMessage("user", user),
                        ),
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return@safeCall Outcome.Failure(AppError.Http(response.status.value, response.bodyAsText().take(300)))
            }
            val content = response.body<ChatResponse>().firstContent
                ?: return@safeCall Outcome.Failure(AppError.Parse("no choices returned"))
            runCatching { Outcome.Success(parse(extractJson(content))) }
                .getOrElse { Outcome.Failure(AppError.Parse(it.message ?: "malformed JSON")) }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(apiKey: String) {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        header("x-api-key", apiKey)
    }

    /** Tolerates models that wrap JSON in prose or code fences. */
    private fun extractJson(raw: String): String {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private inline fun <T> safeCall(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (io: java.io.IOException) {
        Outcome.Failure(AppError.NoNetwork)
    } catch (t: Throwable) {
        Outcome.Failure(AppError.Unknown(t.message ?: "Cloud request failed"))
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name.equals(name, true) } ?: fallback

    private companion object {
        const val CHAT_PATH = "chat/completions"
        const val EMBEDDINGS_PATH = "embeddings"
    }
}
