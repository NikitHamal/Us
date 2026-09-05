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
 * 2. If its confidence is below [CONFIDENCE_THRESHOLD], try the user's selected Nebians model
 *    when cloud AI is enabled and the selection is usable (free providers need no key).
 * 3. If Nebians is unavailable or fails, try the legacy custom cloud endpoint when enabled.
 * 4. If every network call fails, silently keep the offline answer.
 */
class LlmRouter(
    private val offline: LlmProvider,
    private val nebians: LlmProvider,
    private val cloud: LlmProvider,
    private val cloudGate: CloudGate,
    private val nebiansGate: NebiansGate,
) {

    suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis> =
        route(
            offlineCall = { offline.analyzeTone(request) },
            nebiansCall = { nebians.analyzeTone(request) },
            cloudCall = { cloud.analyzeTone(request) },
            confidenceOf = { it.confidence },
        )

    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        route(
            offlineCall = { offline.rephrase(request) },
            nebiansCall = { nebians.rephrase(request) },
            cloudCall = { cloud.rephrase(request) },
            confidenceOf = { it.confidence },
        )

    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        route(
            offlineCall = { offline.extractPatterns(request) },
            nebiansCall = { nebians.extractPatterns(request) },
            cloudCall = { cloud.extractPatterns(request) },
            confidenceOf = { it.confidence },
        )

    suspend fun embed(text: String): Outcome<FloatArray> = offline.embed(text)

    private suspend fun <T> route(
        offlineCall: suspend () -> Outcome<T>,
        nebiansCall: suspend () -> Outcome<T>,
        cloudCall: suspend () -> Outcome<T>,
        confidenceOf: (T) -> Float,
    ): Outcome<T> {
        val offlineResult = offlineCall()
        val offlineValue = offlineResult.valueOrNull

        val needsEscalation = offlineValue == null || confidenceOf(offlineValue) < CONFIDENCE_THRESHOLD
        if (!needsEscalation) return offlineResult

        if (nebiansGate.isNebiansUsable()) {
            val nebiansResult = nebiansCall()
            if (nebiansResult is Outcome.Success) return nebiansResult
            // Fall through to legacy cloud before giving up on the network path.
            if (cloudGate.isCloudUsable()) {
                val cloudResult = cloudCall()
                return when {
                    cloudResult is Outcome.Success -> cloudResult
                    offlineResult is Outcome.Success -> offlineResult
                    else -> nebiansResult
                }
            }
            return if (offlineResult is Outcome.Success) offlineResult else nebiansResult
        }

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

/** Gate for the Nebians fleet: same explicit cloud toggle, provider availability checked separately. */
interface NebiansGate {
    suspend fun isNebiansUsable(): Boolean
}

/** Lets the DI layer feed the router the cloud toggle without a settings dependency. */
interface CloudEnabledSource {
    val enabled: kotlinx.coroutines.flow.Flow<Boolean>
}
