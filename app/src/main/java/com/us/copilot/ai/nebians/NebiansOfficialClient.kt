package com.us.copilot.ai.nebians

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official-format chat client (port of Nebians `api/llm/client.py`).
 *
 * Speaks OpenAI chat-completions (also used by Agnes, DeepSeek and every
 * OpenAI-compatible pool or custom endpoint), the Anthropic Messages API and
 * the Gemini generateContent API, normalising everything to [NebiansChatResult].
 * Keyless pools work because the Authorization header is only sent when a key
 * actually exists — exactly like the server side.
 */
@Singleton
class NebiansOfficialClient @Inject constructor(
    private val http: HttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun chat(
        provider: NebiansProviderSpec,
        model: String,
        messages: List<NebiansMessage>,
        apiKey: String,
        baseUrlOverride: String,
        attachments: List<NebiansAttachment>,
        temperature: Float,
        maxTokens: Int,
    ): NebiansChatResult {
        val baseUrl = baseUrlOverride.trim().ifBlank { provider.baseUrl }.trim().trimEnd('/')
        require(baseUrl.isNotBlank()) { "No endpoint configured for ${provider.slug}" }
        val resolvedModel = model.ifBlank { provider.defaultModel }
        require(resolvedModel.isNotBlank()) { "No model selected for ${provider.slug}" }
        require(messages.isNotEmpty()) { "No messages supplied" }
        return when (provider.format) {
            NebiansWireFormat.ANTHROPIC -> anthropicChat(baseUrl, apiKey, resolvedModel, messages, temperature, maxTokens)
            NebiansWireFormat.GEMINI -> geminiChat(baseUrl, apiKey, resolvedModel, messages, temperature, maxTokens)
            else -> openAiChat(provider, baseUrl, apiKey, resolvedModel, messages, attachments, temperature, maxTokens)
        }
    }

    // --- OpenAI-compatible -------------------------------------------------

    private suspend fun openAiChat(
        provider: NebiansProviderSpec,
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<NebiansMessage>,
        attachments: List<NebiansAttachment>,
        temperature: Float,
        maxTokens: Int,
    ): NebiansChatResult {
        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
        val payload = buildJsonObject {
            put("model", model)
            put("messages", openAiMessages(messages, attachments))
            put("max_tokens", maxTokens)
            put("temperature", temperature.toDouble())
            put("stream", false)
        }
        val raw = postJson(url, openAiHeaders(apiKey, provider), payload)
        val root = json.parseToJsonElement(raw).jsonObject
        root["error"]?.let { throw NebiansException("Upstream error: $it", retryable = false) }
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw NebiansException("Unexpected response shape", retryable = false)
        val content = messageText(message["content"])
        val reasoning = messageText(message["reasoning_content"])
            .ifBlank { messageText(message["reasoning"]) }
        val text = content.ifBlank { reasoning }.trim()
        if (text.isBlank()) throw NebiansException("Empty completion", retryable = true)
        return NebiansChatResult(text = text, reasoning = reasoning.trim())
    }

    private fun openAiMessages(
        messages: List<NebiansMessage>,
        attachments: List<NebiansAttachment>,
    ): JsonArray = buildJsonArray {
        val images = attachments.filter { it.isImage }.take(5)
        messages.forEachIndexed { index, message ->
            val isLastUser = message.normalizedRole == "user" &&
                messages.subList(index, messages.size).none {
                    it !== message && it.normalizedRole == "user"
                }
            if (isLastUser && images.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "content",
                            buildJsonArray {
                                add(buildJsonObject { put("type", "text"); put("text", message.content) })
                                images.forEach { image ->
                                    add(
                                        buildJsonObject {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                buildJsonObject { put("url", image.dataUrl) },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            } else {
                add(buildJsonObject { put("role", message.normalizedRole); put("content", message.content) })
            }
        }
    }

    private fun openAiHeaders(apiKey: String, provider: NebiansProviderSpec): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers.putAll(provider.extraHeaders)
        val effective = apiKey.trim().ifBlank { provider.defaultKey }
        if (effective.isNotBlank()) headers[HttpHeaders.Authorization] = "Bearer $effective"
        return headers
    }

    // --- Anthropic ----------------------------------------------------------

    private suspend fun anthropicChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<NebiansMessage>,
        temperature: Float,
        maxTokens: Int,
    ): NebiansChatResult {
        if (apiKey.isBlank()) throw NebiansException("Anthropic needs an API key", retryable = false)
        val trimmed = baseUrl.trimEnd('/')
        val url = when {
            trimmed.endsWith("/v1/messages") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/messages"
            else -> "$trimmed/v1/messages"
        }
        val convo = messages.conversation().ifEmpty { listOf(NebiansMessage("user", "Hello")) }
        val fixed = if (convo.first().normalizedRole != "user") {
            listOf(NebiansMessage("user", "(continue)")) + convo
        } else {
            convo
        }
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", temperature.toDouble())
            put(
                "messages",
                buildJsonArray {
                    fixed.forEach { add(buildJsonObject { put("role", it.normalizedRole); put("content", it.content) }) }
                },
            )
            messages.systemBlock().takeIf { it.isNotBlank() }?.let { put("system", it) }
        }
        val raw = postJson(
            url,
            mapOf(
                "x-api-key" to apiKey.trim(),
                "anthropic-version" to "2023-06-01",
            ),
            payload,
        )
        val root = json.parseToJsonElement(raw).jsonObject
        root["error"]?.let { throw NebiansException("Upstream error: $it", retryable = false) }
        val parts = root["content"]?.jsonArray.orEmpty()
        val text = parts.filter { it.jsonObject["type"].asString() == "text" }
            .joinToString("") { it.jsonObject["text"].asString().orEmpty() }.trim()
        if (text.isBlank()) throw NebiansException("Empty completion", retryable = true)
        val reasoning = parts.filter { it.jsonObject["type"].asString() == "thinking" }
            .joinToString("\n\n") {
                it.jsonObject["thinking"].asString().orEmpty().trim()
            }.trim()
        return NebiansChatResult(text = text, reasoning = reasoning)
    }

    // --- Gemini --------------------------------------------------------------

    private suspend fun geminiChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<NebiansMessage>,
        temperature: Float,
        maxTokens: Int,
    ): NebiansChatResult {
        if (apiKey.isBlank()) throw NebiansException("Gemini needs an API key", retryable = false)
        val url = "${baseUrl.trimEnd('/')}/models/$model:generateContent"
        val convo = messages.conversation().ifEmpty { listOf(NebiansMessage("user", "Hello")) }
        val payload = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    convo.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", if (message.normalizedRole == "assistant") "model" else "user")
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(buildJsonObject { put("text", message.content) })
                                    },
                                )
                            },
                        )
                    }
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", maxTokens)
                    put("temperature", temperature.toDouble())
                },
            )
            messages.systemBlock().takeIf { it.isNotBlank() }?.let { system ->
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
                    },
                )
            }
        }
        val raw = postJson(url, mapOf("x-goog-api-key" to apiKey.trim()), payload)
        val root = json.parseToJsonElement(raw).jsonObject
        root["error"]?.let { throw NebiansException("Upstream error: $it", retryable = false) }
        val parts = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts.joinToString("") {
            it.jsonObject["text"].asString().orEmpty()
        }.trim()
        if (text.isBlank()) throw NebiansException("Empty completion (possibly blocked)", retryable = false)
        return NebiansChatResult(text = text)
    }

    // --- Transport ------------------------------------------------------------

    private suspend fun postJson(url: String, headers: Map<String, String>, payload: JsonObject): String {
        val body = try {
            http.post(url) {
                headers.forEach { (key, value) -> header(key, value) }
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                timeout { requestTimeoutMillis = 180_000 }
            }
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}", retryable = true)
        }
        val raw = body.bodyAsText()
        if (!body.status.isSuccess()) {
            val retryable = body.status.value in setOf(408, 409, 425, 429, 500, 502, 503, 504)
            throw NebiansException("HTTP ${body.status.value}: ${raw.take(300)}", retryable = retryable)
        }
        return raw
    }

    private fun messageText(element: kotlinx.serialization.json.JsonElement?): String {
        if (element == null || element is kotlinx.serialization.json.JsonNull) return ""
        return try {
            element.asString().orEmpty()
        } catch (e: IllegalArgumentException) {
            try {
                element.jsonArray.joinToString("") { part ->
                    part.jsonObject["text"].asString().orEmpty()
                }
            } catch (e2: IllegalArgumentException) {
                ""
            }
        }
    }
}

