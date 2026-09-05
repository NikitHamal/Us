package com.us.copilot.domain.usecase

import com.us.copilot.ai.agent.AgentCoordinator
import com.us.copilot.ai.agent.AgentHistoryEntry
import com.us.copilot.ai.agent.AgentRequestSpec
import com.us.copilot.ai.agent.AgentTurn
import com.us.copilot.core.util.Outcome
import javax.inject.Inject

/**
 * Open-ended conversation with the agent.
 *
 * Distinct from [BeforeYouSendUseCase], which answers one narrow question about one draft. This is
 * for everything else — "why do we keep having this fight", "what should I say tomorrow" — where
 * the agent needs to go and look things up before it can say anything useful.
 */
class AskCoachUseCase @Inject constructor(
    private val coordinator: AgentCoordinator,
) {
    suspend operator fun invoke(
        message: String,
        history: List<AgentHistoryEntry> = emptyList(),
    ): Outcome<AgentTurn> = coordinator.run(
        AgentRequestSpec(
            systemPrompt = AgentCoordinator.SYSTEM_PROMPT,
            userMessage = message,
            history = history.takeLast(MAX_HISTORY),
        ),
    )

    suspend fun isFullAgentAvailable(): Boolean = coordinator.isFullAgentAvailable()

    private companion object {
        /** Enough for continuity, few enough to keep prompt cost and latency sane. */
        const val MAX_HISTORY = 10
    }
}
