package com.us.copilot.ai.offline

import com.us.copilot.ai.model.ProfileContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool-calling surface exposed to the on-device model (and reusable by the cloud provider).
 *
 * Every tool is pure and synchronous: it takes JSON arguments and returns JSON. That keeps the
 * model sandboxed — it can reason about the relationship but it cannot reach storage or network.
 */
@Singleton
class ToolRegistry @Inject constructor() {

    data class Tool(
        val name: String,
        val description: String,
        val parameters: List<String>,
        val invoke: (JsonObject) -> JsonObject,
    )

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val tools: Map<String, Tool> = listOf(
        Tool(
            name = "detect_horsemen",
            description = "Flags Gottman's Four Horsemen in a message with the exact evidence phrase.",
            parameters = listOf("text"),
        ) { args ->
            val hits = HorsemanDetector.detect(args.str("text"))
            buildJsonObject {
                hits.forEach { put(it.horseman.name.lowercase(), JsonPrimitive(it.evidence)) }
            }
        },
        Tool(
            name = "score_sentiment",
            description = "Returns a sentiment score from -1 (hostile) to +1 (warm).",
            parameters = listOf("text"),
        ) { args ->
            buildJsonObject {
                put("sentiment", JsonPrimitive(Lexicon.sentimentScore(args.str("text"))))
                put("emotion", JsonPrimitive(Lexicon.detectEmotion(args.str("text")).name))
            }
        },
        Tool(
            name = "build_nvc",
            description = "Rewrites a message as Observation, Feeling, Need, Request.",
            parameters = listOf("text"),
        ) { args ->
            val nvc = NvcRephraser.buildNvc(args.str("text"))
            buildJsonObject {
                put("observation", JsonPrimitive(nvc.observation))
                put("feeling", JsonPrimitive(nvc.feeling))
                put("need", JsonPrimitive(nvc.need))
                put("request", JsonPrimitive(nvc.request))
            }
        },
        Tool(
            name = "love_language_tip",
            description = "Suggests an action matched to the partner's primary love language.",
            parameters = listOf("love_language"),
        ) { args ->
            val tip = NvcRephraser.loveLanguageTip(
                ProfileContext(loveLanguages = listOf(args.str("love_language"))),
            )
            buildJsonObject {
                put("language", JsonPrimitive(tip?.language?.label ?: "unknown"))
                put("suggestion", JsonPrimitive(tip?.suggestion ?: ""))
            }
        },
    ).associateBy { it.name }

    fun toolNames(): List<String> = tools.keys.sorted()

    fun schema(): String = json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(ToolSchema.serializer()),
        tools.values.map { ToolSchema(it.name, it.description, it.parameters) },
    )

    fun call(name: String, args: JsonObject): JsonObject =
        tools[name]?.invoke?.invoke(args)
            ?: buildJsonObject { put("error", JsonPrimitive("Unknown tool: $name")) }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    @kotlinx.serialization.Serializable
    data class ToolSchema(val name: String, val description: String, val parameters: List<String>)
}
