package com.us.copilot.ai.nebians

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Multi-format tool-call parser for providers without native function calling
 * (port of Nebians `api/background_agent/protocol.py`).
 *
 * Scraped web models drop OpenAI `tools`, but they are still trained on the
 * Hermes/Nous XML envelope (`<tool_call>…</tool_call>`). This parser accepts
 * that native format first, then Qwen `✿FUNCTION✿` markers, fenced tool
 * blocks, and finally the JSON envelope — so a single turn almost never dies
 * on format. Plain prose with no tool calls means the model is done: its text
 * is the final reply.
 */
data class NebiansToolCall(val name: String, val arguments: JsonObject)

data class ParsedAgentResponse(
    val thought: String,
    val actions: List<NebiansToolCall>,
    val final: String,
    val needsInput: Boolean,
    val protocol: String,
    val parseOk: Boolean,
)

private val THINK_BLOCK = Regex("<\\s*think\\s*>(.*?)<\\s*/\\s*think\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val TOOL_CALL = Regex("<\\s*tool_call\\s*>(.*?)<\\s*/\\s*tool_call\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val TOOL_CALL_OPEN = Regex("<\\s*tool_call\\s*>", RegexOption.IGNORE_CASE)
private val FENCE_TOOL = Regex("```tool[^\\n]*\\n(.*?)```", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val QWEN_FN = Regex("✿FUNCTION✿\\s*:\\s*([A-Za-z_][\\w-]*)\\s*✿ARGS✿\\s*:\\s*(.*?)(?=✿FUNCTION✿|$)", RegexOption.DOT_MATCHES_ALL)
private val NAME_LINE = Regex("^\\s*(?:name|tool)\\s*[=:]\\s*([A-Za-z_][\\w-]*)\\s*$", RegexOption.IGNORE_CASE)

private val DONE_ALIASES = setOf("done", "finish", "submit", "complete")
private val ASK_ALIASES = setOf("ask_user", "ask", "needs_input")

private val jsonLenient = Json { ignoreUnknownKeys = true; isLenient = true }

object AgentProtocol {

    const val MAX_ACTIONS_PER_TURN = 12

    fun parse(raw: String): ParsedAgentResponse {
        var text = raw.trim()
        if (text.isEmpty()) return ParsedAgentResponse("", emptyList(), "", true, "none", false)

        val thinkBits = THINK_BLOCK.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        if (thinkBits.isNotEmpty()) text = THINK_BLOCK.replace(text, "\n").trim()
        val thinkPrefix = thinkBits.joinToString("\n\n")

        val hermes = TOOL_CALL.findAll(text).mapNotNull { parseBody(it.groupValues[1]) }.toList()
        val qwenFn = if (hermes.isEmpty()) {
            QWEN_FN.findAll(text).mapNotNull { match ->
                action(match.groupValues[1], coerceArgs(match.groupValues[2]))
            }.toList()
        } else {
            emptyList()
        }
        val fences = if (hermes.isEmpty() && qwenFn.isEmpty()) {
            FENCE_TOOL.findAll(text).mapNotNull { parseBody(it.groupValues[1]) }.toList()
        } else {
            emptyList()
        }
        // Unclosed trailing <tool_call> (model hit its length limit mid-call).
        val unclosed = if (hermes.isEmpty() && qwenFn.isEmpty() && fences.isEmpty() &&
            TOOL_CALL_OPEN.findAll(text).count() == 1 && !text.contains("</tool_call>", ignoreCase = true)
        ) {
            val tail = text.substring(TOOL_CALL_OPEN.find(text)!!.range.last + 1)
            parseBody(tail)?.let { listOf(it) } ?: emptyList()
        } else {
            emptyList()
        }

        val envelope = extractEnvelope(text)
        val jsonActions = if (envelope != null) actionsFromEnvelope(envelope) else emptyList()

        val protocol: String
        val actions: List<NebiansToolCall>
        when {
            hermes.isNotEmpty() -> { protocol = "hermes"; actions = hermes }
            qwenFn.isNotEmpty() -> { protocol = "qwen_fn"; actions = qwenFn }
            fences.isNotEmpty() -> { protocol = "fence"; actions = fences }
            unclosed.isNotEmpty() -> { protocol = "hermes"; actions = unclosed }
            jsonActions.isNotEmpty() -> { protocol = "json"; actions = jsonActions }
            else -> { protocol = "none"; actions = emptyList() }
        }

        var thought = thinkPrefix
        var final = ""
        var needsInput = false
        if (envelope != null && protocol == "json") {
            if (envelope["thought"].asString()?.isNotBlank() == true) {
                thought = listOf(thought, envelope["thought"]!!.jsonPrimitive.content).filter { it.isNotBlank() }.joinToString("\n\n")
            }
            final = envelope["final"].asString().orEmpty()
            needsInput = envelope["needs_input"].asString()?.toBooleanStrictOrNull() == true
        }

        // Control tools split off from real work.
        val work = mutableListOf<NebiansToolCall>()
        for (call in actions) {
            when (call.name) {
                "done" -> if (final.isBlank()) {
                    final = call.arguments["summary"].asString()
                        ?: call.arguments["final"].asString()
                        ?: call.arguments["message"].asString().orEmpty()
                }
                "ask_user" -> {
                    needsInput = true
                    val question = call.arguments["question"].asString()
                        ?: call.arguments["message"].asString().orEmpty()
                    if (thought.isBlank()) thought = question
                }
                else -> work.add(call)
            }
        }

        if (protocol == "none") {
            // Plain prose: the model answered directly. That is the final text.
            return ParsedAgentResponse(
                thought = thought.ifBlank { text.take(2000) },
                actions = emptyList(),
                final = text,
                needsInput = false,
                protocol = "none",
                parseOk = true,
            )
        }
        if (thought.isBlank() && protocol != "json") {
            var cleaned = TOOL_CALL.replace(text, "\n")
            cleaned = FENCE_TOOL.replace(cleaned, "\n")
            thought = cleaned.trim()
        }
        val capped = work.take(MAX_ACTIONS_PER_TURN)
        return ParsedAgentResponse(
            thought = thought.take(20000),
            actions = capped,
            final = final.take(30000),
            needsInput = needsInput,
            protocol = protocol,
            parseOk = true,
        )
    }

    private fun parseBody(body: String): NebiansToolCall? {
        val text = body.trim()
        if (text.isEmpty()) return null
        decodeObject(text)?.let { obj ->
            fromMapping(obj)?.let { return it }
        }
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            NAME_LINE.matchEntire(lines[0])?.let { match ->
                val rest = lines.drop(1).joinToString("\n").trim()
                return action(match.groupValues[1], if (rest.isEmpty()) emptyMap() else decodeObject(rest) ?: mapOf("value" to rest))
            }
            if (lines.size >= 2 && Regex("^[A-Za-z_][\\w-]*$").matches(lines[0].trim())) {
                return action(lines[0].trim(), decodeObject(lines.drop(1).joinToString("\n")) ?: emptyMap())
            }
        }
        return null
    }

    private fun fromMapping(payload: Map<String, Any?>): NebiansToolCall? {
        val rawName = payload["name"] ?: payload["tool"] ?: payload["function"] ?: return null
        val name = when (rawName) {
            is String -> rawName
            is Map<*, *> -> rawName["name"] as? String ?: return null
            else -> return null
        }
        @Suppress("UNCHECKED_CAST")
        val args = when {
            payload.containsKey("arguments") -> coerceArgs(payload["arguments"])
            payload.containsKey("parameters") -> coerceArgs(payload["parameters"])
            payload.containsKey("args") -> coerceArgs(payload["args"])
            else -> payload.filterKeys { it !in setOf("name", "tool", "function", "thought") }
        } as Map<String, Any?>
        return action(name, args)
    }

    private fun action(name: String, args: Map<String, Any?>): NebiansToolCall? {
        val cleaned = name.trim().replace(Regex("[\\s-]+"), "_").trim('_').lowercase()
        if (cleaned.isEmpty() || !Regex("^[a-z_][a-z0-9_]*$").matches(cleaned)) return null
        val normalized = when (cleaned) {
            in DONE_ALIASES -> "done"
            in ASK_ALIASES -> "ask_user"
            else -> cleaned
        }
        return NebiansToolCall(normalized, toJsonObject(args))
    }

    private fun coerceArgs(value: Any?): Map<String, Any?> = when (value) {
        is Map<*, *> -> @Suppress("UNCHECKED_CAST") (value as Map<String, Any?>)
        is String -> decodeObject(value) ?: if (value.isBlank()) emptyMap() else mapOf("value" to value)
        else -> emptyMap()
    }

    private fun decodeObject(text: String): Map<String, Any?>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Try whole string, then trailing-comma repair, then largest {...} span.
        decodeJsonObject(trimmed)?.let { return it }
        decodeJsonObject(Regex(",\\s*([}\\]])").replace(trimmed, "$1"))?.let { return it }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) decodeJsonObject(trimmed.substring(start, end + 1))?.let { return it }
        return null
    }

    private fun decodeJsonObject(text: String): Map<String, Any?>? = try {
        val element = jsonLenient.parseToJsonElement(text)
        if (element !is JsonObject) return null
        element.mapValues { (_, value) -> jsonValue(value) }
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun jsonValue(element: kotlinx.serialization.json.JsonElement): Any? = when (element) {
        is kotlinx.serialization.json.JsonObject -> element.mapValues { (_, v) -> jsonValue(v) }
        is kotlinx.serialization.json.JsonArray -> element.map { jsonValue(it) }
        is kotlinx.serialization.json.JsonNull -> null
        is kotlinx.serialization.json.JsonPrimitive ->
            if (element.isString) {
                element.content
            } else {
                element.content.toLongOrNull()
                    ?: element.content.toDoubleOrNull()
                    ?: element.content.toBooleanStrictOrNull()
                    ?: element.content
            }
    }

    private fun toJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        map.forEach { (key, value) -> put(key, toElement(value)) }
    }

    private fun toElement(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> kotlinx.serialization.json.JsonPrimitive(value)
        is Number -> kotlinx.serialization.json.JsonPrimitive(value)
        is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            @Suppress("UNCHECKED_CAST")
            (value as Map<String, Any?>).forEach { (k, v) -> put(k, toElement(v)) }
        }
        is List<*> -> buildJsonArray { value.forEach { add(toElement(it)) } }
        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
    }

    private fun extractEnvelope(text: String): JsonObject? {
        val candidates = mutableListOf(text)
        Regex("```(?:json)?\\s*(\\{.*?\\})\\s*```", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(text).forEach { candidates.add(it.groupValues[1]) }
        candidates.addAll(balancedSpans(text))
        val seen = mutableSetOf<String>()
        var single: JsonObject? = null
        for (candidate in candidates) {
            if (!seen.add(candidate)) continue
            val obj = try {
                jsonLenient.parseToJsonElement(candidate) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } ?: continue
            if (obj.keys.any { it in setOf("actions", "thought", "final", "needs_input", "summary") }) return obj
            if (single == null && obj.keys.any { it == "tool" || it == "name" }) single = obj
        }
        return single
    }

    private fun balancedSpans(text: String): List<String> {
        val out = mutableListOf<String>()
        text.forEachIndexed { start, char ->
            if (char != '{') return@forEachIndexed
            var depth = 0
            var quoted = false
            var escaped = false
            for (index in start until text.length) {
                val current = text[index]
                if (quoted) {
                    if (escaped) escaped = false
                    else if (current == '\\') escaped = true
                    else if (current == '"') quoted = false
                    continue
                }
                when (current) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            out.add(text.substring(start, index + 1))
                            break
                        }
                    }
                }
            }
        }
        return out
    }

    private fun actionsFromEnvelope(envelope: JsonObject): List<NebiansToolCall> {
        val actions = envelope["actions"]
        if (actions is JsonObject) return listOfNotNull(singleAction(actions))
        val array = actions as? kotlinx.serialization.json.JsonArray ?: return singleAction(envelope)?.let { listOf(it) } ?: emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let { obj -> singleAction(obj) } }
    }

    private fun singleAction(obj: JsonObject): NebiansToolCall? {
        val rawName = obj["name"].asString()
            ?: obj["tool"].asString()
            ?: obj["function"].asString()
            ?: return null
        val argsElement = obj["arguments"] ?: obj["parameters"] ?: obj["args"]
        val args = if (argsElement is JsonObject) {
            argsElement.mapValues { (_, v) -> jsonValue(v) }
        } else {
            obj.filterKeys { it !in setOf("name", "tool", "function", "thought") }
                .mapValues { (_, v) -> jsonValue(v) }
        }
        return action(rawName, args)
    }
}
