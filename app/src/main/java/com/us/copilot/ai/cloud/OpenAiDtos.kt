package com.us.copilot.ai.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for any OpenAI-compatible `/chat/completions` and `/embeddings` endpoint. */

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.4,
    @SerialName("max_tokens") val maxTokens: Int = 900,
    @SerialName("response_format") val responseFormat: ResponseFormat? = ResponseFormat(),
)

@Serializable
data class ResponseFormat(val type: String = "json_object")

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList()) {
    val firstContent: String? get() = choices.firstOrNull()?.message?.content
}

@Serializable
data class ChatChoice(val message: ChatMessage, @SerialName("finish_reason") val finishReason: String? = null)

@Serializable
data class EmbeddingRequest(val model: String, val input: String)

@Serializable
data class EmbeddingResponse(val data: List<EmbeddingData> = emptyList())

@Serializable
data class EmbeddingData(val embedding: List<Float> = emptyList())

/** Structured JSON we ask the model to return, mirrored onto our domain types. */

@Serializable
data class ToneDto(
    val sentiment: Float = 0f,
    val emotion: String = "NEUTRAL",
    val harshness: Int = 0,
    val horsemen: List<HorsemanDto> = emptyList(),
    val triggers: List<String> = emptyList(),
    val summary: String = "",
    val risk: String = "LOW",
    val confidence: Float = 0.8f,
)

@Serializable
data class HorsemanDto(val type: String, val evidence: String = "")

@Serializable
data class RephraseDto(
    val soft: String = "",
    @SerialName("soft_why") val softWhy: String = "",
    val direct: String = "",
    @SerialName("direct_why") val directWhy: String = "",
    val playful: String = "",
    @SerialName("playful_why") val playfulWhy: String = "",
    @SerialName("love_language") val loveLanguage: String = "",
    @SerialName("love_language_tip") val loveLanguageTip: String = "",
    val observation: String = "",
    val feeling: String = "",
    val need: String = "",
    val request: String = "",
    val confidence: Float = 0.85f,
)

@Serializable
data class PatternDto(
    val themes: List<ThemeDto> = emptyList(),
    val observations: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val confidence: Float = 0.85f,
)

@Serializable
data class ThemeDto(val label: String, val occurrences: Int = 1, val note: String = "")
