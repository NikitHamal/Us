package com.us.copilot.ai.agent

import com.us.copilot.ai.cloud.AgentMessage
import com.us.copilot.ai.cloud.AgentRequest
import com.us.copilot.ai.cloud.AgentResponse
import com.us.copilot.ai.cloud.FunctionSchemaDto
import com.us.copilot.ai.cloud.ToolDefinitionDto
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.ai.CloudGate
import com.us.copilot.domain.repository.CloudCredentials
import com.us.copilot.domain.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-turn tool-calling agent over any OpenAI-compatible endpoint.
 *
 * The loop is: send messages + tool schemas -> if the model returns tool_calls, execute them all,
 * append the results as `tool` messages, and send again -> repeat until the model returns prose.
 *
 * Tool failures are fed back to the model as text rather than aborting. A model that asked for a
 * profile that does not exist should be told so and allowed to carry on, exactly as it would if a
 * person answered "we never filled that in".
 */
@Singleton
class CloudAgentRunner @Inject constructor(
    private val client: HttpClient,
    private val settings: SettingsRepository,
    private val cloudGate: CloudGate,
    private val toolbox: AgentToolbox,
) : AgentRunner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun isAvailable(): Boolean {
        if (!cloudGate.isCloudUsable()) return false
        return settings.cloudCredentials().isComplete
    }

    override suspend fun run(request: AgentRequestSpec): Outcome<AgentTurn> {
        if (!cloudGate.isCloudUsable()) return Outcome.Failure(AppError.CloudDisabled)

        val credentials = settings.cloudCredentials()
        if (!credentials.isComplete) return Outcome.Failure(AppError.MissingCredentials)

        val tools = toolbox.all().map { tool ->
            ToolDefinitionDto(
                function = FunctionSchemaDto(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters,
                ),
            )
        }

        val messages = mutableListOf(AgentMessage(role = "system", content = request.systemPrompt))
        request.history.forEach { entry ->
            messages += AgentMessage(
                role = if (entry.isUser) "user" else "assistant",
                content = entry.text,
            )
        }
        messages += AgentMessage(role = "user", content = request.userMessage)

        val steps = mutableListOf<AgentStep>()

        repeat(AgentRunner.MAX_ITERATIONS) { iteration ->
            val response = postChat(credentials, messages, tools)
                ?: return Outcome.Failure(AppError.Unknown("Agent request failed."))

            if (response is Outcome.Failure) return response

            val body = (response as Outcome.Success).value
            val message = body.message
                ?: return Outcome.Failure(AppError.Parse("No message in agent response."))

            val calls = message.toolCalls.orEmpty()
            if (calls.isEmpty()) {
                val reply = message.content?.trim().orEmpty()
                if (reply.isBlank()) {
                    return Outcome.Failure(AppError.Parse("Agent returned an empty reply."))
                }
                return Outcome.Success(AgentTurn(reply = reply, steps = steps))
            }

            // Echo the assistant's tool-call turn back, or the follow-up loses its context.
            messages += message

            calls.forEach { call ->
                val tool = toolbox.find(call.function.name)
                val result = if (tool == null) {
                    ToolResult.Failure("Unknown tool '${call.function.name}'.")
                } else {
                    val args = parseArguments(call.function.arguments)
                    runCatching { tool.execute(args) }
                        .getOrElse { ToolResult.Failure(it.message ?: "Tool threw an exception.") }
                }

                steps += AgentStep(
                    toolName = call.function.name,
                    summary = result.asModelText.lineSequence().firstOrNull().orEmpty().take(120),
                    isMutating = tool?.isMutating == true,
                    succeeded = result is ToolResult.Success,
                )

                messages += AgentMessage(
                    role = "tool",
                    content = result.asModelText,
                    toolCallId = call.id,
                    name = call.function.name,
                )
            }

            // On the final permitted iteration, stop asking for tools and demand an answer.
            if (iteration == AgentRunner.MAX_ITERATIONS - 2) {
                messages += AgentMessage(
                    role = "system",
                    content = "Stop calling tools now and answer the user with what you have.",
                )
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

    private suspend fun postChat(
        credentials: CloudCredentials,
        messages: List<AgentMessage>,
        tools: List<ToolDefinitionDto>,
    ): Outcome<AgentResponse>? = runCatching {
        val response = client.post(credentials.endpoint("chat/completions")) {
            header(HttpHeaders.Authorization, "Bearer ${credentials.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(
                AgentRequest(
                    model = credentials.modelName,
                    messages = messages,
                    tools = tools,
                ),
            )
        }
        if (!response.status.isSuccess()) {
            return Outcome.Failure(AppError.Http(response.status.value, response.body<String>()))
        }
        Outcome.Success(response.body<AgentResponse>())
    }.getOrElse { throwable ->
        Outcome.Failure(AppError.Unknown(throwable.message ?: "Agent network failure."))
    }

    /** Models occasionally emit malformed or empty argument strings; treat those as no args. */
    private fun parseArguments(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
}
