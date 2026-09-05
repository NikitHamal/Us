package com.us.copilot.ai

import com.us.copilot.ai.model.PatternReport
import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.util.Outcome

/**
 * Offline-first routing policy.
 *
 * 1. Always run the on-device provider first — it costs nothing and leaks nothing.
 * 2. If its confidence is below [CONFIDENCE_THRESHOLD] **and** the user has explicitly enabled
 *    cloud AI **and** credentials exist, re-run the request on the cloud provider.
 * 3. If the cloud call fails for any reason, silently keep the offline answer.
 */
class LlmRouter(
    private val offline: LlmProvider,
    private val cloud: LlmProvider,
    private val cloudGate: CloudGate,
) {

    suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis> =
        route(
            offlineCall = { offline.analyzeTone(request) },
            cloudCall = { cloud.analyzeTone(request) },
            confidenceOf = { it.confidence },
        )

    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        route(
            offlineCall = { offline.rephrase(request) },
            cloudCall = { cloud.rephrase(request) },
            confidenceOf = { it.confidence },
        )

    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        route(
            offlineCall = { offline.extractPatterns(request) },
            cloudCall = { cloud.extractPatterns(request) },
            confidenceOf = { it.confidence },
        )

    suspend fun embed(text: String): Outcome<FloatArray> = offline.embed(text)

    private suspend fun <T> route(
        offlineCall: suspend () -> Outcome<T>,
        cloudCall: suspend () -> Outcome<T>,
        confidenceOf: (T) -> Float,
    ): Outcome<T> {
        val offlineResult = offlineCall()
        val offlineValue = offlineResult.valueOrNull

        val needsEscalation = offlineValue == null || confidenceOf(offlineValue) < CONFIDENCE_THRESHOLD
        if (!needsEscalation) return offlineResult
        if (!cloudGate.isCloudUsable()) return offlineResult

        val cloudResult = cloudCall()
        return when {
            cloudResult is Outcome.Success -> cloudResult
            offlineResult is Outcome.Success -> offlineResult
            else -> cloudResult
        }
    }
}

/** Indirection so the router does not depend on the settings repository implementation. */
interface CloudGate {
    suspend fun isCloudUsable(): Boolean
}

/** Lets the DI layer feed the router the cloud toggle without a settings dependency. */
interface CloudEnabledSource {
    val enabled: kotlinx.coroutines.flow.Flow<Boolean>
}
