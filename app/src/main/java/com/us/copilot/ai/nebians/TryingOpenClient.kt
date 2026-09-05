package com.us.copilot.ai.nebians

import com.us.copilot.domain.repository.NebiansEffort
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TryingOpen native client (port of Nebians `api/tryingopen_proxy.py`).
 *
 * TryingOpen is the backbone of the Nebians free fleet on this phone: 16
 * open models behind one keyless endpoint, with an explicit reasoning-effort
 * selector (quick / balanced / deep) and native file upload (images + docs as
 * data URLs, up to 3 files of 5 MB). The response is a Vercel AI SDK v5 SSE
 * stream; we read the whole body and fold the deltas, exactly like the
 * server's `simple_chat`.
 */
@Singleton
class TryingOpenClient @Inject constructor(
    private val http: HttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun chat(
        provider: NebiansProviderSpec,
        model: String,
        messages: List<NebiansMessage>,
        effort: NebiansEffort,
        attachments: List<NebiansAttachment>,
    ): NebiansChatResult {
        val resolvedModel = model.ifBlank { provider.defaultModel }
        require(resolvedModel.isNotBlank()) { "No TryingOpen model selected" }
        require(messages.isNotEmpty()) { "No messages supplied" }

        val apiMessages = toApiMessages(messages, attachments)
        require(apiMessages.isNotEmpty()) { "No messages to send" }

        val payload = buildJsonObject {
            put("id", UUID.randomUUID().toString().replace("-", "").take(16))
            put("trigger", "submit-message")
            put("model", resolvedModel)
            put("effort", effort.name.lowercase())
            put("messages", apiMessages)
        }

        val raw = try {
            val response = http.post(provider.baseUrl) {
                header(HttpHeaders.Accept, "text/event-stream")
                header(HttpHeaders.Origin, "https://www.tryingopen.com")
                header(HttpHeaders.Referrer, "https://www.tryingopen.com/")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                timeout { requestTimeoutMillis = 300_000 }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw NebiansException("TryingOpen HTTP ${response.status.value}: ${body.take(300)}")
            }
            body
        } catch (e: NebiansException) {
            throw e
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}")
        }

        return foldStream(raw)
    }

    private fun toApiMessages(
        messages: List<NebiansMessage>,
        attachments: List<NebiansAttachment>,
    ) = buildJsonArray {
        val system = messages.systemBlock()
        val convo = messages.conversation()
        val files = attachments.take(MAX_FILES)
        convo.forEachIndexed { index, message ->
            val isFirstUser = message.normalizedRole == "user" &&
                convo.subList(0, index).none { it.normalizedRole == "user" }
            val isLastUser = message.normalizedRole == "user" &&
                convo.subList(index, convo.size).count { it.normalizedRole == "user" } == 1
            var text = message.content
            if (isFirstUser && system.isNotBlank()) {
                text = if (text.isBlank()) system else "$system\n\n$text"
            }
            if (text.isBlank() && !(isLastUser && files.isNotEmpty())) return@forEachIndexed
            add(
                buildJsonObject {
                    put("id", "m$index")
                    put("role", message.normalizedRole)
                    put(
                        "parts",
                        buildJsonArray {
                            if (isLastUser) {
                                files.forEach { file ->
                                    add(
                                        buildJsonObject {
                                            put("type", "file")
                                            put("filename", file.filename)
                                            put("mediaType", file.mimeType)
                                            put("url", file.dataUrl)
                                        },
                                    )
                                }
                            }
                            if (text.isNotBlank()) {
                                add(buildJsonObject { put("type", "text"); put("text", text) })
                            }
                        },
                    )
                },
            )
        }
    }

    private fun foldStream(raw: String): NebiansChatResult {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        for (payload in parseSsePayloads(raw)) {
            val event = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
            when (event["type"].asString()) {
                "reasoning-delta" -> reasoning.append(event["delta"].asString().orEmpty())
                "text-delta" -> text.append(event["delta"].asString().orEmpty())
                "error" -> {
                    val message = event["errorText"].asString()
                        ?: event["error"].asString()
                        ?: "TryingOpen error"
                    if (text.isBlank()) throw NebiansException(message)
                }
            }
        }
        val answer = text.toString().trim()
        if (answer.isBlank()) throw NebiansException("TryingOpen returned no content")
        return NebiansChatResult(text = answer, reasoning = reasoning.toString().trim())
    }

    companion object {
        const val MAX_FILES = 3
        const val MAX_FILE_BYTES = 5 * 1024 * 1024L
    }
}
