package com.us.copilot.ai.agent

import com.us.copilot.ai.nebians.NebiansAgentRunner
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses which runner handles a turn.
 *
 * Nebians models are the default engine and the legacy custom endpoint is the
 * fallback. Failures surface as real errors — there is no offline mode to
 * degrade into, so a failing provider is visible instead of masquerading as
 * an on-device answer.
 */
@Singleton
class AgentCoordinator @Inject constructor(
    private val cloud: CloudAgentRunner,
    private val nebians: NebiansAgentRunner,
) {

    suspend fun run(request: AgentRequestSpec): Outcome<AgentTurn> {
        var lastFailure: Outcome.Failure? = null
        if (cloud.isAvailable()) {
            when (val result = cloud.run(request)) {
                is Outcome.Success -> return result
                is Outcome.Failure -> lastFailure = result
            }
        }
        if (nebians.isAvailable()) {
            return nebians.run(request)
        }
        return lastFailure ?: Outcome.Failure(AppError.MissingCredentials)
    }

    /** True when a real tool-calling agent is driving. */
    suspend fun isFullAgentAvailable(): Boolean = cloud.isAvailable() || nebians.isAvailable()

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
