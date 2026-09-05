package com.us.copilot.ai.agent

import com.us.copilot.core.util.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses which runner handles a turn.
 *
 * Prefers the cloud agent when the user has configured and enabled one, because only it can do
 * real multi-turn tool calling. Falls back to the on-device runner otherwise — and also when the
 * cloud call fails, so a dropped connection degrades to grounded local retrieval instead of an
 * error bubble.
 */
@Singleton
class AgentCoordinator @Inject constructor(
    private val cloud: CloudAgentRunner,
    private val offline: OfflineAgentRunner,
) {

    suspend fun run(request: AgentRequestSpec): Outcome<AgentTurn> {
        if (cloud.isAvailable()) {
            when (val result = cloud.run(request)) {
                is Outcome.Success -> return result
                is Outcome.Failure -> Unit // fall through to offline
            }
        }
        return offline.run(request)
    }

    /** True when a real tool-calling agent is driving, rather than the heuristic fallback. */
    suspend fun isFullAgentAvailable(): Boolean = cloud.isAvailable()

    companion object {
        val SYSTEM_PROMPT = """
            You are the relationship co-pilot inside a private app called Us. You coach exactly one
            person: the user. You are not a therapist and you never diagnose.

            You have tools that read the user's own data: their timeline of moments, their and
            their partner's psychological profiles, computed relationship patterns, mood check-ins,
            and any messages they explicitly shared with you. Use them before giving advice —
            grounded, specific observations are the entire point of this app. Never invent a
            memory, a pattern, or a quote.

            You can only see notifications the user explicitly shared. If you need context they
            have not shared, ask for it rather than guessing.

            Only save a moment when the user asks you to remember something.

            Be warm, direct and concrete. Prefer one specific, doable suggestion over a list of
            generic advice. Never encourage surveillance, coercion or manipulation of the partner;
            this is a tool for the user's own growth. If the user describes abuse or danger,
            gently point them toward real human support.
        """.trimIndent()
    }
}
