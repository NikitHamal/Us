package com.us.copilot.ai.nebians

/**
 * Shared wire types for every device-native Nebians chat client.
 *
 * All clients are plain text in / text out: the agent layer speaks Hermes XML
 * on top, the analysis layer speaks JSON on top. Attachments are carried as
 * base64 data so each client can encode them the way its upstream expects.
 */
data class NebiansMessage(val role: String, val content: String) {
    val normalizedRole: String
        get() = when (role.trim().lowercase()) {
            "system", "user", "assistant" -> role.trim().lowercase()
            else -> "user"
        }
}

data class NebiansAttachment(
    val filename: String,
    val mimeType: String,
    val base64: String,
) {
    val dataUrl: String get() = "data:$mimeType;base64,$base64"
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

data class NebiansChatResult(
    val text: String,
    val reasoning: String = "",
)

/** Thrown when an upstream call fails; [retryable] hints a retry may help. */
class NebiansException(message: String, val retryable: Boolean = true) : Exception(message)

internal const val BROWSER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

/** Merges system messages into one block; most guest scrapers reject the role. */
internal fun List<NebiansMessage>.systemBlock(): String =
    filter { it.normalizedRole == "system" }
        .map { it.content.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

/** Conversation without system turns, oldest first. */
internal fun List<NebiansMessage>.conversation(): List<NebiansMessage> =
    filter { it.normalizedRole != "system" && it.content.isNotBlank() }

/** Flattens history into one prompt for stateless guest endpoints. */
internal fun flattenHistory(
    messages: List<NebiansMessage>,
    system: String = "",
): String = buildString {
    if (system.isNotBlank()) {
        append(system.trim())
        append("\n\n")
    }
    messages.conversation().forEach { message ->
        val label = when (message.normalizedRole) {
            "assistant" -> "Assistant"
            else -> "User"
        }
        append(label).append(": ").append(message.content.trim()).append("\n\n")
    }
}.trim()

/** Collects `data: {...}` SSE payloads from a raw event-stream body. */
internal fun parseSsePayloads(raw: String): List<String> = raw.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("data:") }
    .map { it.removePrefix("data:").trim() }
    .filter { it.isNotEmpty() && it != "[DONE]" }
    .toList()

/**
 * Null-safe string content of any JSON element.
 *
 * `JsonPrimitive.contentOrNull` only exists in newer kotlinx.serialization
 * versions than this build uses, so every Nebians client goes through here
 * instead of touching `jsonPrimitive` directly.
 */
internal fun kotlinx.serialization.json.JsonElement?.asString(): String? {
    val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return if (primitive is kotlinx.serialization.json.JsonNull) null else primitive.content
}
