package com.us.copilot.ai.agent

import kotlinx.serialization.json.JsonObject

/**
 * A capability the agent may invoke during a turn.
 *
 * Tools are the only way the model reaches app data. That is deliberate: rather than stuffing the
 * whole database into a prompt, the agent asks for exactly what it needs, which keeps context
 * small and — more importantly — makes every data access an auditable, permission-checked call.
 *
 * Read tools are safe to run unattended. Write tools ([isMutating]) change user data, so the
 * runner surfaces them in the transcript rather than letting them happen invisibly.
 */
interface AgentTool {

    val name: String

    /** Shown to the model. Must state exactly when to use the tool and what it returns. */
    val description: String

    /** JSON Schema for the arguments object, as the OpenAI function-calling format expects. */
    val parameters: JsonObject

    /** True if invoking this tool changes stored user data. */
    val isMutating: Boolean get() = false

    /**
     * Runs the tool. Implementations must never throw: failures come back as
     * [ToolResult.Failure] so the agent can read the error and adapt rather than
     * collapsing the whole turn.
     */
    suspend fun execute(arguments: JsonObject): ToolResult
}

sealed interface ToolResult {
    /** [content] is fed back to the model verbatim, so it must be compact and self-describing. */
    data class Success(val content: String) : ToolResult

    data class Failure(val message: String) : ToolResult

    val asModelText: String
        get() = when (this) {
            is Success -> content
            is Failure -> "ERROR: $message"
        }
}

/** A tool call the model asked for. */
data class ToolInvocation(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

/** One step in an agent run, kept so the UI can show its reasoning trail. */
data class AgentStep(
    val toolName: String,
    val summary: String,
    val isMutating: Boolean,
    val succeeded: Boolean,
)
