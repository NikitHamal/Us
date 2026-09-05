package com.us.copilot.ai.agent.tools

import com.us.copilot.ai.agent.AgentTool
import com.us.copilot.ai.agent.ToolResult
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemoryFilter
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** Reads recent timeline entries. */
class RecentMemoriesTool @Inject constructor(
    private val memories: MemoryRepository,
) : AgentTool {

    override val name = "recent_memories"

    override val description =
        "Read the most recent moments from the relationship timeline. Use this to ground advice " +
            "in what actually happened rather than guessing. Returns newest first."

    override val parameters = schema(
        "limit" to intProperty("How many moments to return. Default 10, max 40."),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val limit = arguments.intOr("limit", default = 10).coerceIn(1, 40)
        val items = memories.observeRecent(limit).first()
        if (items.isEmpty()) return ToolResult.Success("No moments recorded yet.")
        return ToolResult.Success(items.joinToString("\n") { it.asLine() })
    }
}

/** Keyword/emotion/unresolved search over the timeline. */
class SearchMemoriesTool @Inject constructor(
    private val memories: MemoryRepository,
) : AgentTool {

    override val name = "search_memories"

    override val description =
        "Search the timeline by keyword, emotion, or unresolved status. Use this to check whether " +
            "a pattern the user mentions is actually recurring, and how often."

    override val parameters = schema(
        "query" to stringProperty("Free-text keyword to match against moment text."),
        "emotion" to stringProperty(
            "Optional emotion filter. One of: " + Emotion.entries.joinToString(", ") { it.name },
        ),
        "unresolved_only" to boolProperty("If true, only return moments still marked unresolved."),
        "limit" to intProperty("Max results. Default 15, max 40."),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val limit = arguments.intOr("limit", default = 15).coerceIn(1, 40)
        val query = arguments.stringOr("query").orEmpty()
        val emotion = arguments.stringOr("emotion")
            ?.let { name -> Emotion.entries.firstOrNull { it.name.equals(name, true) } }
        val unresolvedOnly = arguments.boolOr("unresolved_only", default = false)

        val results = memories.observe(
            MemoryFilter(
                query = query,
                emotions = emotion?.let { setOf(it) } ?: emptySet(),
                onlyUnresolved = unresolvedOnly,
            ),
        ).first().take(limit)

        if (results.isEmpty()) return ToolResult.Success("No matching moments.")
        return ToolResult.Success(
            "${results.size} match(es):\n" + results.joinToString("\n") { it.asLine() },
        )
    }
}

/** Writes a new moment. Mutating, so the runner shows it in the transcript. */
class SaveMemoryTool @Inject constructor(
    private val memories: MemoryRepository,
) : AgentTool {

    override val name = "save_memory"

    override val description =
        "Save a moment to the timeline. Only use this when the user asks you to remember " +
            "something, or clearly describes an event worth recording. Never save silently."

    override val isMutating = true

    override val parameters = schema(
        "text" to stringProperty("What happened, in the user's own words where possible."),
        "emotion" to stringProperty(
            "Emotional tone. One of: " + Emotion.entries.joinToString(", ") { it.name },
        ),
        "is_unresolved" to boolProperty("True if this is still an open issue between them."),
        required = listOf("text"),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val text = arguments.stringOr("text")?.trim()
        if (text.isNullOrBlank()) return ToolResult.Failure("text is required")

        val emotion = arguments.stringOr("emotion")
            ?.let { name -> Emotion.entries.firstOrNull { it.name.equals(name, true) } }
            ?: Emotion.NEUTRAL

        val id = memories.add(
            Memory(
                text = text,
                emotion = emotion,
                intensity = 3,
                timestamp = System.currentTimeMillis(),
                source = MemorySource.MANUAL,
                speaker = Speaker.ME,
                isUnresolved = arguments.boolOr("is_unresolved", default = false),
            ),
        )
        return ToolResult.Success("Saved moment #$id.")
    }
}

private fun Memory.asLine(): String = buildString {
    append("- [").append(TimeUtils.relative(timestamp)).append("] ")
    append('(').append(emotion.name.lowercase()).append(") ")
    if (isUnresolved) append("[UNRESOLVED] ")
    append(text.replace('\n', ' ').take(220))
}
