package com.us.copilot.ai

import com.us.copilot.ai.model.PatternReport
import com.us.copilot.ai.model.PatternRequest
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.model.ProviderId
import com.us.copilot.core.util.Outcome

/**
 * The single seam between the app and any language model.
 *
 * Implementations must be pure with respect to storage: they receive everything they need in the
 * request object and never touch the database. Swapping a provider (on-device model, our own
 * proxy, a scraper endpoint, a hosted API) is therefore a one-line change in [LlmRouter].
 */
interface LlmProvider {

    val id: ProviderId

    /** Cheap readiness probe: model file present / credentials configured. */
    suspend fun isAvailable(): Boolean

    suspend fun analyzeTone(request: ToneRequest): Outcome<ToneAnalysis>

    suspend fun rephrase(request: RephraseRequest): Outcome<RephraseSet>

    suspend fun extractPatterns(request: PatternRequest): Outcome<PatternReport>

    /** Vector for semantic search over memories. */
    suspend fun embed(text: String): Outcome<FloatArray>
}
