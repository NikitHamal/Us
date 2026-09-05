package com.us.copilot.ai.nebians

import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
 * Keyless guest-scraper clients (ports of Nebians `k2think_proxy.py`,
 * `poolside_proxy.py`, `motiftech_proxy.py`, `yqcloud_proxy.py` and
 * `chatjimmy_proxy.py`).
 *
 * Every endpoint is an anonymous guest POST with plain browser headers — no
 * cookies, no tokens, no signing — so they run directly from the phone over
 * the shared [HttpClient]. Responses are folded non-streaming: the whole body
 * is read, then deltas are collected exactly like each proxy's `simple_chat`.
 * None of these supports native tool calls or file upload; the agent layer
 * drives them through the Hermes `<tool_call>` XML protocol instead.
 */
@Singleton
class GuestScraperClients @Inject constructor(
    private val http: HttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun chat(
        provider: NebiansProviderSpec,
        model: String,
        messages: List<NebiansMessage>,
    ): NebiansChatResult = when (provider.format) {
        NebiansWireFormat.K2THINK -> k2Think(model.ifBlank { provider.defaultModel }, messages)
        NebiansWireFormat.POOLSIDE -> poolside(model.ifBlank { provider.defaultModel }, messages)
        NebiansWireFormat.MOTIF -> motif(model.ifBlank { provider.defaultModel }, messages)
        NebiansWireFormat.YQCLOUD -> yqcloud(messages)
        NebiansWireFormat.CHATJIMMY -> chatJimmy(model.ifBlank { provider.defaultModel }, messages)
        else -> throw NebiansException("Not a guest scraper: ${provider.slug}", retryable = false)
    }

    // --- K2Think ------------------------------------------------------------

    private suspend fun k2Think(model: String, messages: List<NebiansMessage>): NebiansChatResult {
        val payload = buildJsonObject {
            put("stream", true)
            put("model", model)
            put("messages", k2History(messages))
            put("params", buildJsonObject {})
            put("features", buildJsonObject { put("web_search", false) })
            put(
                "extra_body",
                buildJsonObject {
                    put(
                        "chat_template_kwargs",
                        buildJsonObject { put("reasoning_effort", "medium") },
                    )
                },
            )
        }
        val raw = guestPost(
            url = "https://chat.ifm.ai/api/guest/chat/completions",
            origin = "https://chat.ifm.ai",
            referer = "https://chat.ifm.ai/guest",
            payload = payload,
            acceptStream = true,
        )
        var lastContent = ""
        for (chunk in parseSsePayloads(raw)) {
            val event = runCatching { json.parseToJsonElement(chunk).jsonObject }.getOrNull() ?: continue
            if (event.containsKey("task_id")) continue
            val detail = event["detail"].asString()
            if (detail != null && !event.containsKey("content")) {
                throw NebiansException("K2Think: ${detail.take(300)}")
            }
            event["content"].asString()?.let { lastContent = it }
        }
        if (lastContent.isBlank()) throw NebiansException("K2Think returned no content")
        val split = lastContent.lastIndexOf("</details>")
        val reasoning = if (split >= 0) stripDetails(lastContent.substring(0, split)) else ""
        val answer = if (split >= 0) lastContent.substring(split + "</details>".length).trim() else stripDetails(lastContent)
        if (answer.isBlank()) throw NebiansException("K2Think returned no content")
        return NebiansChatResult(text = answer, reasoning = reasoning)
    }

    private fun k2History(messages: List<NebiansMessage>) = buildJsonArray {
        messages.forEach { message ->
            add(
                buildJsonObject {
                    put("role", message.normalizedRole)
                    put("content", message.content)
                    if (message.normalizedRole == "assistant") put("reasoning", "")
                },
            )
        }
    }

    private fun stripDetails(text: String): String {
        var out = text.replace(Regex("<details\\b[^>]*>"), "")
            .replace(Regex("<summary\\b[^>]*>.*?</summary>", RegexOption.DOT_MATCHES_ALL), "")
            .replace("</details>", "")
        out = out.lineSequence()
            .joinToString("\n") { line -> if (line.startsWith("> ")) line.drop(2) else line }
        return out.trim()
    }

    // --- Poolside ------------------------------------------------------------

    private suspend fun poolside(model: String, messages: List<NebiansMessage>): NebiansChatResult {
        val upstreamModel = "poolside/$model"
        val createPayload = buildJsonObject {
            put("title", "us")
            put("model", upstreamModel)
            put("inferenceMode", "platform")
            put("incognito", false)
        }
        val created = guestPost(
            url = "https://chat.poolside.ai/api/chats",
            origin = "https://chat.poolside.ai",
            referer = "https://chat.poolside.ai/guest",
            payload = createPayload,
            acceptStream = false,
        )
        val chatId = runCatching { json.parseToJsonElement(created).jsonObject["id"].asString() }.getOrNull()
        if (chatId.isNullOrBlank()) throw NebiansException("Poolside could not start a chat")

        val messageId = UUID.randomUUID().toString()
        val generationId = UUID.randomUUID().toString()
        val sendPayload = buildJsonObject {
            put("chatId", chatId)
            put("model", upstreamModel)
            put("inferenceMode", "platform")
            put(
                "options",
                buildJsonObject {
                    put("webSearch", true); put("slack", false); put("slackWrite", false); put("thinking", true)
                },
            )
            put("id", chatId)
            put("trigger", "submit-message")
            put("messageId", messageId)
            put("baseMessageId", kotlinx.serialization.json.JsonNull)
            put(
                "message",
                buildJsonObject {
                    put("messageId", messageId)
                    put(
                        "parts",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", flattenHistory(messages, messages.systemBlock()))
                                },
                            )
                        },
                    )
                    put("id", messageId)
                    put("role", "user")
                },
            )
            put("generationId", generationId)
        }
        val sent = try {
            val response = http.post("https://chat.poolside.ai/api/chat") {
                guestHeaders("https://chat.poolside.ai", "https://chat.poolside.ai/guest", acceptStream = false)
                header("x-poolside-stream-protocol", "resumable-v1")
                contentType(ContentType.Application.Json)
                setBody(sendPayload.toString())
                timeout { requestTimeoutMillis = 60_000 }
            }
            val body = response.bodyAsText()
            if (response.status.value != 202) throw NebiansException("Poolside send HTTP ${response.status.value}: ${body.take(200)}")
            body
        } catch (e: NebiansException) {
            throw e
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}")
        }
        val streamUrl = runCatching {
            json.parseToJsonElement(sent).jsonObject["streamUrl"].asString()
        }.getOrNull()
        if (streamUrl.isNullOrBlank()) throw NebiansException("Poolside send missing stream")

        val streamRaw = try {
            val response = http.get("https://chat.poolside.ai$streamUrl") {
                header(HttpHeaders.Accept, "text/event-stream")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                timeout { requestTimeoutMillis = 300_000 }
            }
            if (!response.status.isSuccess()) throw NebiansException("Poolside stream HTTP ${response.status.value}")
            response.bodyAsText()
        } catch (e: NebiansException) {
            throw e
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}")
        }
        val answer = StringBuilder()
        for (chunk in parseSsePayloads(streamRaw)) {
            val event = runCatching { json.parseToJsonElement(chunk).jsonObject }.getOrNull() ?: continue
            if (event["type"].asString() == "text-delta") {
                answer.append(event["delta"].asString().orEmpty())
            }
        }
        val text = answer.toString().trim()
        if (text.isBlank()) throw NebiansException("Poolside returned no content")
        return NebiansChatResult(text = text)
    }

    // --- Motif ---------------------------------------------------------------

    private suspend fun motif(model: String, messages: List<NebiansMessage>): NebiansChatResult {
        val lastUser = messages.lastOrNull { it.normalizedRole == "user" }?.content?.trim().orEmpty()
        if (lastUser.isBlank()) throw NebiansException("No user message to send", retryable = false)
        val prior = messages.filter { it.normalizedRole != "system" && it.content.isNotBlank() }.dropLast(1)
        val query = if (prior.isEmpty()) {
            lastUser
        } else {
            val transcript = prior.joinToString("\n\n") { message ->
                "${if (message.normalizedRole == "user") "User" else "Assistant"}: ${message.content.trim()}"
            }
            "Previous conversation:\n$transcript\n\nCurrent message: $lastUser"
        }
        val payload = buildJsonObject {
            put("query", query)
            put("project_id", kotlinx.serialization.json.JsonNull)
            put("template_id", kotlinx.serialization.json.JsonNull)
            put("forced_skill_names", buildJsonArray {})
            put("disabled_skill_names", buildJsonArray {})
            put("language", "en")
            // NOTE: "Katmandu" (one h) is the backend's literal string; keep it byte-identical.
            put("timezone", "Asia/Katmandu")
            put("mode", "chat")
            put("reasoning_effort", "high")
            put("model", model)
        }
        val raw = guestPost(
            url = "https://chat.motiftech.io/api/v1/enterprise/chat",
            origin = "https://chat.motiftech.io",
            referer = "https://chat.motiftech.io/chat",
            payload = payload,
            acceptStream = true,
        )
        val answer = StringBuilder()
        for (chunk in parseSsePayloads(raw)) {
            val event = runCatching { json.parseToJsonElement(chunk).jsonObject }.getOrNull() ?: continue
            val type = event["type"].asString().orEmpty()
            val data = event["data"].asString().orEmpty()
            when {
                type == "chat.text" && data.isNotEmpty() -> answer.append(data)
                type == "shared.sign" -> break
                "error" in type -> throw NebiansException(data.take(300).ifBlank { "Motif error" })
            }
        }
        val text = answer.toString().trim()
        if (text.isBlank()) throw NebiansException("Motif returned no content")
        return NebiansChatResult(text = text)
    }

    // --- Yqcloud ---------------------------------------------------------------

    private suspend fun yqcloud(messages: List<NebiansMessage>): NebiansChatResult {
        val prompt = messages.conversation().joinToString("\n\n") { message ->
            if (messages.conversation().size == 1) message.content.trim()
            else "${if (message.normalizedRole == "user") "User" else "Assistant"}: ${message.content.trim()}"
        }.trim()
        if (prompt.isBlank()) throw NebiansException("No user content to send", retryable = false)
        val payload = buildJsonObject {
            put("prompt", prompt)
            put("userId", "#/chat/${System.currentTimeMillis()}")
            put("network", true)
            put("system", messages.systemBlock())
            put("withoutContext", false)
            put("stream", true)
        }
        // Raw text stream, not SSE.
        val raw = try {
            val response = http.post("https://api.binjie.fun/api/generateStream") {
                guestHeaders("https://chat9.yqcloud.top", "https://chat9.yqcloud.top/", acceptStream = false)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                timeout { requestTimeoutMillis = 180_000 }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw NebiansException("Yqcloud HTTP ${response.status.value}: ${body.take(200)}")
            body
        } catch (e: NebiansException) {
            throw e
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}")
        }
        val text = raw.trim()
        if (text.isBlank()) throw NebiansException("Yqcloud returned no content")
        return NebiansChatResult(text = text)
    }

    // --- ChatJimmy ---------------------------------------------------------------

    private suspend fun chatJimmy(model: String, messages: List<NebiansMessage>): NebiansChatResult {
        val convo = buildJsonArray {
            messages.systemBlock().takeIf { it.isNotBlank() }?.let { system ->
                add(buildJsonObject { put("role", "system"); put("content", system) })
            }
            messages.conversation().forEach { message ->
                add(buildJsonObject { put("role", message.normalizedRole); put("content", message.content) })
            }
        }
        if (convo.isEmpty()) throw NebiansException("No user content to send", retryable = false)
        val payload = buildJsonObject {
            put("messages", convo)
            put(
                "chatOptions",
                buildJsonObject { put("selectedModel", model); put("topK", 8) },
            )
        }
        val raw = try {
            val response = http.post("https://chatjimmy.ai/api/chat") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.Origin, "https://chatjimmy.ai")
                header(HttpHeaders.Referrer, "https://chatjimmy.ai/")
                header("X-Real-IP", "24.1.${(0..254).random()}.${(1..254).random()}")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                timeout { requestTimeoutMillis = 180_000 }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw NebiansException("ChatJimmy HTTP ${response.status.value}: ${body.take(200)}")
            body
        } catch (e: NebiansException) {
            throw e
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}")
        }
        val text = raw.replace(Regex("<\\|stats\\|>.*?</\\|stats\\|>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<\\|think\\|>.*?</\\|think\\|>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
        if (text.isBlank()) throw NebiansException("ChatJimmy returned no content")
        return NebiansChatResult(text = text)
    }

    // --- Transport ------------------------------------------------------------

    private suspend fun guestPost(
        url: String,
        origin: String,
        referer: String,
        payload: kotlinx.serialization.json.JsonObject,
        acceptStream: Boolean,
    ): String {
        try {
            val response = http.post(url) {
                guestHeaders(origin, referer, acceptStream)
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
                timeout { requestTimeoutMillis = 300_000 }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw NebiansException("HTTP ${response.status.value}: ${body.take(300)}")
            }
            return body
        } catch (e: NebiansException) {
            throw e
        } catch (e: Exception) {
            throw NebiansException("Network error: ${e.message}")
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.guestHeaders(
        origin: String,
        referer: String,
        acceptStream: Boolean,
    ) {
        header(HttpHeaders.Accept, if (acceptStream) "text/event-stream, application/json" else "application/json, text/plain, */*")
        header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        header(HttpHeaders.Origin, origin)
        header(HttpHeaders.Referrer, referer)
        header(HttpHeaders.UserAgent, BROWSER_UA)
        header("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Google Chrome\";v=\"151\", \"Chromium\";v=\"151\"")
        header("sec-ch-ua-mobile", "?0")
        header("sec-ch-ua-platform", "\"Windows\"")
    }
}
