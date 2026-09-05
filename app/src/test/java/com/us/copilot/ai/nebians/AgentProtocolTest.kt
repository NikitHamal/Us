package com.us.copilot.ai.nebians

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the Hermes/XML tool protocol ported from the Nebians backend. */
class AgentProtocolTest {

    @Test
    fun `hermes tool call parses name and arguments`() {
        val raw = """
            Let me check your timeline first.
            <tool_call>
            {"name": "recent_memories", "arguments": {"limit": 5}}
            </tool_call>
        """.trimIndent()
        val parsed = AgentProtocol.parse(raw)
        assertEquals("hermes", parsed.protocol)
        assertEquals(1, parsed.actions.size)
        assertEquals("recent_memories", parsed.actions[0].name)
        assertTrue(parsed.parseOk)
    }

    @Test
    fun `multiple hermes calls in one turn all parse`() {
        val raw = """
            <tool_call>{"name": "read_profile", "arguments": {"owner": "PARTNER"}}</tool_call>
            <tool_call>{"name": "read_check_ins", "arguments": {}}</tool_call>
        """.trimIndent()
        val parsed = AgentProtocol.parse(raw)
        assertEquals(2, parsed.actions.size)
        assertEquals("read_profile", parsed.actions[0].name)
        assertEquals("read_check_ins", parsed.actions[1].name)
    }

    @Test
    fun `plain prose is a final answer with no actions`() {
        val raw = "That sounds really tough. Have you tried naming what you need first?"
        val parsed = AgentProtocol.parse(raw)
        assertTrue(parsed.actions.isEmpty())
        assertEquals(raw, parsed.final)
        assertTrue(parsed.parseOk)
    }

    @Test
    fun `json envelope with actions parses`() {
        val raw = """{"thought": "checking", "actions": [{"tool": "search_memories", "arguments": {"query": "fight"}}]}"""
        val parsed = AgentProtocol.parse(raw)
        assertEquals("json", parsed.protocol)
        assertEquals(1, parsed.actions.size)
        assertEquals("search_memories", parsed.actions[0].name)
        assertEquals("checking", parsed.thought)
    }

    @Test
    fun `done control tool becomes final`() {
        val raw = """<tool_call>{"name": "done", "arguments": {"summary": "All done, validated."}}</tool_call>"""
        val parsed = AgentProtocol.parse(raw)
        assertTrue(parsed.actions.isEmpty())
        assertEquals("All done, validated.", parsed.final)
    }

    @Test
    fun `ask_user control tool sets needs input`() {
        val raw = """<tool_call>{"name": "ask_user", "arguments": {"question": "Which week?"}}</tool_call>"""
        val parsed = AgentProtocol.parse(raw)
        assertTrue(parsed.actions.isEmpty())
        assertTrue(parsed.needsInput)
    }

    @Test
    fun `qwen function markers parse`() {
        val raw = "✿FUNCTION✿: recent_memories ✿ARGS✿: {\"limit\": 3}"
        val parsed = AgentProtocol.parse(raw)
        assertEquals("qwen_fn", parsed.protocol)
        assertEquals("recent_memories", parsed.actions[0].name)
    }

    @Test
    fun `think blocks move to thought, not actions`() {
        val raw = "<think>private reasoning here</think>\nJust talk to her gently."
        val parsed = AgentProtocol.parse(raw)
        assertTrue(parsed.actions.isEmpty())
        assertEquals("Just talk to her gently.", parsed.final)
        assertTrue(parsed.thought.contains("private reasoning here"))
    }

    @Test
    fun `empty input asks for input`() {
        val parsed = AgentProtocol.parse("   ")
        assertTrue(parsed.needsInput)
    }
}
