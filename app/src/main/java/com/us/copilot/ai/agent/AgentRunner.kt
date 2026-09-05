package com.us.copilot.ai.agent

import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome

/**
 * Runs an agent turn: the model may call tools, read the results, and call more tools, until it
 * produces a final answer or hits [MAX_ITERATIONS].
 *
 * The iteration cap is not a formality. Models loop — re-reading the same memories, or calling a
 * failing tool forever — and every iteration is a network round trip the user waits through. When
 * the cap is hit we return whatever prose we have rather than an error, because a partial answer
 * grounded in real tool output still beats a spinner that dies.
 */
interface AgentRunner {

    /** True when this runner can actually do multi-turn tool calling right now. */
    suspend fun isAvailable(): Boolean

    suspend fun run(request: AgentRequestSpec): Outcome<AgentTurn>

    companion object {
        const val MAX_ITERATIONS = 6
    }
}

data class AgentRequestSpec(
    val systemPrompt: String,
    val userMessage: String,
    /** Prior turns, oldest first, so the agent has conversational memory. */
    val history: List<AgentHistoryEntry> = emptyList(),
    /** Files attached to this turn (screenshots, docs). Only file-capable models receive them. */
    val attachments: List<AgentAttachment> = emptyList(),
)

data class AgentHistoryEntry(val isUser: Boolean, val text: String)

/** One file carried with an agent turn, as base64 so any client can encode it. */
data class AgentAttachment(
    val filename: String,
    val mimeType: String,
    val base64: String,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/** The result of one agent turn. */
data class AgentTurn(
    val reply: String,
    /** Tools the agent used, in order — surfaced in the UI so its work is visible. */
    val steps: List<AgentStep> = emptyList(),
    val hitIterationCap: Boolean = false,
)

/** Shared failure for when no agent-capable provider is configured. */
internal val NoAgentProvider: Outcome<AgentTurn> = Outcome.Failure(
    AppError.Unknown("No agent-capable model is configured."),
)
