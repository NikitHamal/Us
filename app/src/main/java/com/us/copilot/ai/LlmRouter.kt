package com.us.copilot.ai

import com.us.copilot.ai.model.PatternReport
import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.util.Outcome

/**
 * Routing policy.
 *
 * Nebians is the default engine: the user's selected model answers first, the
 * legacy custom endpoint is the fallback, and failures surface as real errors
 * instead of degrading into on-device answers. There is no silent offline
 * mode — when nothing can answer, the caller gets the actual failure.
 */
class LlmRouter(
    private val offline: LlmProvider,
    private val nebians: LlmProvider,
    private val cloud: LlmProvider,
    private val cloudGate: CloudGate,
) {

    suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis> =
        route(
            nebiansCall = { nebians.analyzeTone(request) },
            cloudCall = { cloud.analyzeTone(request) },
        )

    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        route(
            nebiansCall = { nebians.rephrase(request) },
            cloudCall = { cloud.rephrase(request) },
        )

    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        route(
            nebiansCall = { nebians.extractPatterns(request) },
            cloudCall = { cloud.extractPatterns(request) },
        )

    suspend fun embed(text: String): Outcome<FloatArray> = offline.embed(text)

    private suspend fun <T> route(
        nebiansCall: suspend () -> Outcome<T>,
        cloudCall: suspend () -> Outcome<T>,
    ): Outcome<T> {
        val nebiansResult = nebiansCall()
        if (nebiansResult is Outcome.Success) return nebiansResult
        if (cloudGate.isCloudUsable()) {
            val cloudResult = cloudCall()
            if (cloudResult is Outcome.Success) return cloudResult
        }
        return nebiansResult
    }
}

/** Indirection so the router does not depend on the settings repository implementation. */
interface CloudGate {
    suspend fun isCloudUsable(): Boolean
}

/** Gate for the Nebians fleet: free providers are always usable, key-required ones need credentials. */
interface NebiansGate {
    suspend fun isNebiansUsable(): Boolean
}

/** Lets the DI layer feed the router the cloud toggle without a settings dependency. */
interface CloudEnabledSource {
    val enabled: kotlinx.coroutines.flow.Flow<Boolean>
}
