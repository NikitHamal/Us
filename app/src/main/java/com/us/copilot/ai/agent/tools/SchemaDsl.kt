package com.us.copilot.ai.agent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Tiny helpers for declaring JSON Schema tool parameters.
 *
 * Hand-writing nested buildJsonObject blocks for every tool obscures what the tool actually
 * takes; these keep each declaration to one readable line per field.
 */

internal fun stringProperty(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

internal fun intProperty(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

internal fun boolProperty(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

internal fun schema(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        properties.forEach { (key, value) -> put(key, value) }
    }
    put(
        "required",
        buildJsonArray { required.forEach { add(JsonPrimitive(it)) } },
    )
}

// --- Argument readers. Tolerant by design: models routinely send numbers as strings. ---

internal fun JsonObject.stringOr(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull()

internal fun JsonObject.intOr(key: String, default: Int): Int =
    this[key]?.jsonPrimitive?.contentOrNull()?.toIntOrNull() ?: default

internal fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.contentOrNull()?.toBooleanStrictOrNull() ?: default

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
