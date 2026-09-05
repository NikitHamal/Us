package com.us.copilot.ai.agent

import com.us.copilot.core.util.Outcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device fallback agent.
 *
 * Be clear about what this is: the bundled `.cact` model cannot do real tool calling, so this
 * does NOT reason about which tools to use. It runs a fixed, keyword-triggered plan over the same
 * [AgentToolbox] and stitches the results into a readable answer.
 *
 * That is genuinely useful — it grounds replies in the user's real data with zero network — but it
 * is deterministic retrieval, not autonomy. It is deliberately read-only: mutating tools are never
 * invoked here, because a heuristic has no business writing to someone's relationship history.
 * Configure a cloud model for the real multi-turn agent.
 */
@Singleton
class OfflineAgentRunner @Inject constructor(
    private val toolbox: AgentToolbox,
) : AgentRunner {

    override suspend fun isAvailable(): Boolean = true

    override suspend fun run(request: AgentRequestSpec): Outcome<AgentTurn> {
        val query = request.userMessage.lowercase()
        val plan = buildPlan(query)
        val steps = mutableListOf<AgentStep>()
        val sections = mutableListOf<String>()

        plan.forEach { (toolName, args) ->
            val tool = toolbox.find(toolName) ?: return@forEach
            if (tool.isMutating) return@forEach

            val result = runCatching { tool.execute(args) }
                .getOrElse { ToolResult.Failure(it.message ?: "failed") }

            steps += AgentStep(
                toolName = toolName,
                summary = result.asModelText.lineSequence().firstOrNull().orEmpty().take(120),
                isMutating = false,
                succeeded = result is ToolResult.Success,
            )
            if (result is ToolResult.Success) {
                sections += "${labelFor(toolName)}\n${result.content}"
            }
        }

        val reply = if (sections.isEmpty()) {
            OFFLINE_NO_DATA
        } else {
            buildString {
                appendLine(OFFLINE_PREAMBLE)
                appendLine()
                append(sections.joinToString("\n\n"))
            }
        }

        return Outcome.Success(AgentTurn(reply = reply, steps = steps))
    }

    /**
     * Keyword routing. Crude on purpose — it must be predictable, since there is no model
     * deciding anything. Anything unrecognised falls back to recent context.
     */
    private fun buildPlan(query: String): List<Pair<String, JsonObject>> = buildList {
        val wantsPatterns = PATTERN_WORDS.any { it in query }
        val wantsProfile = PROFILE_WORDS.any { it in query }
        val wantsMood = MOOD_WORDS.any { it in query }
        val wantsShared = SHARED_WORDS.any { it in query }

        if (wantsPatterns) add("read_patterns" to JsonObject(emptyMap()))
        if (wantsProfile) {
            add(
                "read_profile" to JsonObject(
                    mapOf("owner" to JsonPrimitive(if ("my " in query) "ME" else "PARTNER")),
                ),
            )
        }
        if (wantsMood) add("read_check_ins" to JsonObject(emptyMap()))
        if (wantsShared) add("read_shared_notifications" to JsonObject(emptyMap()))

        val keywords = query.split(' ').filter { it.length > 4 }
        if (keywords.isNotEmpty()) {
            add(
                "search_memories" to JsonObject(
                    mapOf("query" to JsonPrimitive(keywords.take(3).joinToString(" "))),
                ),
            )
        }

        if (isEmpty()) add("recent_memories" to JsonObject(emptyMap()))
    }

    private fun labelFor(toolName: String): String = when (toolName) {
        "read_patterns" -> "What the patterns show:"
        "read_profile" -> "From the profile:"
        "read_check_ins" -> "Recent check-ins:"
        "read_shared_notifications" -> "From messages you shared:"
        "search_memories" -> "Related moments:"
        else -> "Recent moments:"
    }

    private companion object {
        val PATTERN_WORDS = listOf("pattern", "always", "keep", "again", "trend", "often", "fight")
        val PROFILE_WORDS = listOf("attachment", "love language", "profile", "trigger", "style")
        val MOOD_WORDS = listOf("mood", "feeling", "felt", "lately", "week", "stress")
        val SHARED_WORDS = listOf("said", "message", "text", "wrote", "sent")

        const val OFFLINE_PREAMBLE =
            "Working offline, so here is what I found in your own records rather than an " +
                "interpretation. Turn on a cloud model in Settings for a full conversation."

        const val OFFLINE_NO_DATA =
            "I could not find anything relevant on device yet. Add a few moments or check-ins " +
                "first, or enable a cloud model in Settings for a fuller answer."
    }
}
