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
 * An explicitly selected Nebians model wins: when cloud AI is enabled and the
 * selection is usable (free providers need no key), it answers first and the
 * on-device engine is only a fallback. Picking a model is consent to use it —
 * silently answering on-device anyway is what made the selector feel dead.
 * With cloud AI off, everything stays on-device and nothing leaves the phone.
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
        )

    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet> =
        route(
            offlineCall = { offline.rephrase(request) },
            nebiansCall = { nebians.rephrase(request) },
            cloudCall = { cloud.rephrase(request) },
        )

    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport> =
        route(
            offlineCall = { offline.extractPatterns(request) },
            nebiansCall = { nebians.extractPatterns(request) },
            cloudCall = { cloud.extractPatterns(request) },
        )

    suspend fun embed(text: String): Outcome<FloatArray> = offline.embed(text)

    private suspend fun <T> route(
        offlineCall: suspend () -> Outcome<T>,
        nebiansCall: suspend () -> Outcome<T>,
        cloudCall: suspend () -> Outcome<T>,
    ): Outcome<T> {
        if (nebiansGate.isNebiansUsable()) {
            val nebiansResult = nebiansCall()
            if (nebiansResult is Outcome.Success) return nebiansResult
            if (cloudGate.isCloudUsable()) {
                val cloudResult = cloudCall()
                if (cloudResult is Outcome.Success) return cloudResult
            }
            val offlineResult = offlineCall()
            return if (offlineResult is Outcome.Success) offlineResult else nebiansResult
        }

        if (cloudGate.isCloudUsable()) {
            val cloudResult = cloudCall()
            if (cloudResult is Outcome.Success) return cloudResult
        }
        return offlineCall()
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
