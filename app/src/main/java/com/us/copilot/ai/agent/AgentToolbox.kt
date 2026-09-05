package com.us.copilot.ai.agent

import com.us.copilot.ai.agent.tools.ReadCheckInsTool
import com.us.copilot.ai.agent.tools.ReadPatternsTool
import com.us.copilot.ai.agent.tools.ReadProfileTool
import com.us.copilot.ai.agent.tools.ReadSharedNotificationsTool
import com.us.copilot.ai.agent.tools.RecentMemoriesTool
import com.us.copilot.ai.agent.tools.SaveMemoryTool
import com.us.copilot.ai.agent.tools.SearchMemoriesTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The complete set of capabilities available to the agent.
 *
 * Registered explicitly rather than via multibinding so the full surface the model can reach is
 * readable in one place — for a privacy-sensitive app, "what can the AI touch?" should be
 * answerable by reading a single list.
 */
@Singleton
class AgentToolbox @Inject constructor(
    recentMemories: RecentMemoriesTool,
    searchMemories: SearchMemoriesTool,
    saveMemory: SaveMemoryTool,
    readProfile: ReadProfileTool,
    readPatterns: ReadPatternsTool,
    readCheckIns: ReadCheckInsTool,
    readSharedNotifications: ReadSharedNotificationsTool,
) {

    private val tools: List<AgentTool> = listOf(
        recentMemories,
        searchMemories,
        readProfile,
        readPatterns,
        readCheckIns,
        readSharedNotifications,
        saveMemory,
    )

    fun all(): List<AgentTool> = tools

    fun find(name: String): AgentTool? = tools.firstOrNull { it.name == name }
}
