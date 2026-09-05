package com.us.copilot.ai.nebians

import com.us.copilot.ai.NebiansGate
import com.us.copilot.ai.agent.AgentRequestSpec
import com.us.copilot.ai.agent.AgentRunner
import com.us.copilot.ai.agent.AgentStep
import com.us.copilot.ai.agent.AgentToolbox
import com.us.copilot.ai.agent.AgentTurn
import com.us.copilot.ai.agent.ToolResult
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.domain.repository.SettingsRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Autonomous tool-calling agent over Nebians providers that have no native
 * function calling (port of the Nebians background-agent harness).
 *
 * The loop mirrors `api/background_agent/runner.py`: the system prompt
 * advertises every tool as Hermes `<tool_call>` XML schemas, the model
 * answers with tool calls or prose, tool results are fed back as
 * `<tool_response>` blocks, and the turn ends when the model replies with
 * plain prose (or calls `done`). Unknown tools and tool failures are fed back
 * as text so the model can adapt instead of dying — exactly like the server.
 *
 * This is what makes scraped web models "agentic" despite having no `tools`
 * parameter: the protocol is in prose, the execution is local, and every data
 * access stays an auditable [AgentToolbox] call.
 */
@Singleton
class NebiansAgentRunner @Inject constructor(
    private val settings: SettingsRepository,
    private val dispatcher: NebiansDispatcher,
    private val toolbox: AgentToolbox,
    private val gate: NebiansGate,
) : AgentRunner {

    override suspend fun isAvailable(): Boolean {
        if (!gate.isNebiansUsable()) return false
        return try {
            val config = settings.nebiansConfigSnapshot()
            NebiansCatalog.find(config.providerSlug) != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun run(request: AgentRequestSpec): Outcome<AgentTurn> {
        if (!gate.isNebiansUsable()) return Outcome.Failure(AppError.MissingCredentials)
        val config = try {
            settings.nebiansConfigSnapshot()
        } catch (e: Exception) {
            return Outcome.Failure(AppError.Storage("Nebians settings are unreadable."))
        }
        if (NebiansCatalog.find(config.providerSlug) == null) {
            return Outcome.Failure(AppError.MissingCredentials)
        }

        val system = buildSystemPrompt()
        val transcript = mutableListOf<NebiansMessage>()
        request.history.forEach { entry ->
            transcript += NebiansMessage(if (entry.isUser) "user" else "assistant", entry.text)
        }
        transcript += NebiansMessage("user", request.userMessage)

        val steps = mutableListOf<AgentStep>()
        var lastActionSignature = ""
        var actionRepeats = 0

        repeat(AgentRunner.MAX_ITERATIONS) { iteration ->
            val messages = mutableListOf(NebiansMessage("system", system))
            messages.addAll(transcript)
            if (iteration == AgentRunner.MAX_ITERATIONS - 2) {
                messages += NebiansMessage(
                    "system",
                    "Stop calling tools now and answer the user with what you have.",
                )
            }

            val reply = try {
                dispatcher.chat(
                    config = config,
                    messages = messages,
                    attachments = if (iteration == 0) {
                        request.attachments.map {
                            NebiansAttachment(it.filename, it.mimeType, it.base64)
                        }
                    } else {
                        emptyList()
                    },
                    maxTokensOverride = 1_200,
                ).text
            } catch (e: NebiansException) {
                android.util.Log.w(TAG, "Nebians agent turn failed: ${e.message}")
                return Outcome.Failure(AppError.Unknown(e.message ?: "Nebians request failed"))
            } catch (io: java.io.IOException) {
                android.util.Log.w(TAG, "Nebians agent network unreachable: ${io.message}")
                return Outcome.Failure(AppError.NoNetwork)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                return Outcome.Failure(AppError.Unknown(t.message ?: "Nebians request failed"))
            }
            if (reply.isBlank()) return Outcome.Failure(AppError.Parse("Agent returned an empty reply."))

            val parsed = AgentProtocol.parse(reply)

            if (parsed.actions.isEmpty()) {
                val answer = parsed.final.ifBlank { reply.trim() }
                if (answer.isBlank()) return Outcome.Failure(AppError.Parse("Agent returned an empty reply."))
                return Outcome.Success(AgentTurn(reply = answer, steps = steps))
            }

            val signature = parsed.actions.joinToString("|") { it.name }
            if (signature == lastActionSignature && signature.isNotBlank()) actionRepeats++ else {
                actionRepeats = 0
                lastActionSignature = signature
            }
            if (actionRepeats >= 3) {
                return Outcome.Success(
                    AgentTurn(
                        reply = "I went in circles checking the same things. " +
                            "Here is what I can say from what I already found — ask me to dig somewhere specific.",
                        steps = steps,
                        hitIterationCap = true,
                    ),
                )
            }

            transcript += NebiansMessage("assistant", reply)
            val results = StringBuilder()
            parsed.actions.take(AgentProtocol.MAX_ACTIONS_PER_TURN).forEach { call ->
                val tool = toolbox.find(call.name)
                val result = if (tool == null) {
                    ToolResult.Failure("Unknown tool '${call.name}'.")
                } else {
                    runCatching { tool.execute(call.arguments) }
                        .getOrElse { ToolResult.Failure(it.message ?: "Tool threw an exception.") }
                }
                steps += AgentStep(
                    toolName = call.name,
                    summary = result.asModelText.lineSequence().firstOrNull().orEmpty().take(120),
                    isMutating = tool?.isMutating == true,
                    succeeded = result is ToolResult.Success,
                )
                results.appendLine("<tool_response>")
                results.appendLine(
                    buildJsonObject {
                        put("name", call.name)
                        put("result", result.asModelText.take(6_000))
                    }.toString(),
                )
                results.appendLine("</tool_response>")
            }
            transcript += NebiansMessage("user", results.toString().trim())

            if (parsed.final.isNotBlank()) {
                return Outcome.Success(AgentTurn(reply = parsed.final, steps = steps))
            }
        }

        return Outcome.Success(
            AgentTurn(
                reply = "I gathered what I could but ran out of steps before finishing.",
                steps = steps,
                hitIterationCap = true,
            ),
        )
    }

    private fun buildSystemPrompt(): String {
        val toolsXml = toolbox.all().joinToString("\n") { tool ->
            buildJsonObject {
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    },
                )
            }.toString()
        }
        return com.us.copilot.ai.agent.AgentCoordinator.SYSTEM_PROMPT + "\n\n" +
            FN_CALL_TEMPLATE.replace("{tool_descs}", toolsXml)
    }

    companion object {
        private const val TAG = "NebiansAgent"
        private const val FN_CALL_TEMPLATE = """# Tools

You may call one or more functions to assist with the user query.

You are provided with function signatures within <tools></tools> XML tags:
<tools>
{tool_descs}
</tools>

For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:
<tool_call>
{"name": <function-name>, "arguments": <args-json-object>}
</tool_call>

Rules:
- Use tools before giving advice: grounded, specific observations are the entire point.
- If you can answer without tools, answer directly in plain prose.
- After tools answer, reply in plain prose. Never wrap your final answer in JSON, fences or tool calls.
- Only save a moment when the user asks you to remember something."""
    }
}
