package com.us.copilot.ai.nebians

import com.us.copilot.ai.NebiansGate
import com.us.copilot.ai.agent.AgentRequestSpec
import com.us.copilot.ai.agent.AgentRunner
import com.us.copilot.ai.agent.AgentTool
import com.us.copilot.ai.agent.AgentToolbox
import com.us.copilot.ai.agent.ToolResult
import com.us.copilot.domain.repository.NebiansConfig
import com.us.copilot.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the autonomous XML tool loop over keyless Nebians models. */
class NebiansAgentRunnerTest {

    private val memoriesTool = object : AgentTool {
        override val name = "recent_memories"
        override val description = "Read recent moments."
        override val parameters: JsonObject = buildJsonObject {
            put("type", "object")
        }
        override suspend fun execute(arguments: JsonObject): ToolResult =
            ToolResult.Success("- [today] (calm) we cooked together")
    }

    private fun runner(
        turns: List<String>,
        nebiansUsable: Boolean = true,
    ): NebiansAgentRunner {
        val settings = mockk<SettingsRepository>()
        coEvery { settings.nebiansConfigSnapshot() } returns NebiansConfig()
        val dispatcher = mockk<NebiansDispatcher>()
        val toolbox = mockk<AgentToolbox>()
        every { toolbox.all() } returns listOf(memoriesTool)
        every { toolbox.find(any()) } answers { name ->
            if (firstArg<String>() == "recent_memories") memoriesTool else null
        }
        var index = 0
        coEvery { dispatcher.chat(any(), any(), any(), any()) } answers {
            NebiansChatResult(text = turns[minOf(index++, turns.lastIndex)])
        }
        val gate = object : NebiansGate {
            override suspend fun isNebiansUsable(): Boolean = nebiansUsable
        }
        return NebiansAgentRunner(settings, dispatcher, toolbox, gate)
    }

    @Test
    fun `prose reply returns without touching tools`() = runTest {
        val result = runner(listOf("Just talk to her gently.")).run(
            AgentRequestSpec(systemPrompt = "sys", userMessage = "help"),
        )
        assertTrue(result.isSuccess)
        assertEquals("Just talk to her gently.", result.valueOrNull?.reply)
        assertTrue(result.valueOrNull?.steps?.isEmpty() == true)
    }

    @Test
    fun `tool call executes then final prose answers`() = runTest {
        val result = runner(
            listOf(
                """<tool_call>{"name": "recent_memories", "arguments": {}}</tool_call>""",
                "You cooked together recently — build on that warmth.",
            ),
        ).run(AgentRequestSpec(systemPrompt = "sys", userMessage = "what worked before?"))
        assertTrue(result.isSuccess)
        val turn = result.valueOrNull!!
        assertEquals(1, turn.steps.size)
        assertEquals("recent_memories", turn.steps[0].toolName)
        assertTrue(turn.steps[0].succeeded)
        assertEquals("You cooked together recently — build on that warmth.", turn.reply)
    }

    @Test
    fun `unavailable gate means not available`() = runTest {
        assertEquals(false, runner(listOf("x"), nebiansUsable = false).isAvailable())
    }

    @Test
    fun `iteration cap returns partial answer`() = runTest {
        val endless = List(AgentRunner.MAX_ITERATIONS + 2) {
            """<tool_call>{"name": "recent_memories", "arguments": {}}</tool_call>"""
        }
        val result = runner(endless).run(AgentRequestSpec(systemPrompt = "sys", userMessage = "hi"))
        assertTrue(result.isSuccess)
        assertTrue(result.valueOrNull?.hitIterationCap == true)
    }
}
