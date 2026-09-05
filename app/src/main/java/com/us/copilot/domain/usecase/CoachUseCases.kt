package com.us.copilot.domain.usecase

import com.us.copilot.ai.LlmRouter
import com.us.copilot.ai.model.RephraseRequest
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.util.Outcome
import javax.inject.Inject

/** Analyses a draft (or a received message) with both profiles as context. */
class AnalyzeToneUseCase @Inject constructor(
    private val router: LlmRouter,
    private val buildContext: BuildProfileContextUseCase,
) {
    suspend operator fun invoke(text: String, authorIsMe: Boolean = true): Outcome<ToneAnalysis> =
        router.analyzeTone(
            ToneRequest(
                text = text,
                authorIsMe = authorIsMe,
                partner = buildContext(ProfileOwner.PARTNER),
                me = buildContext(ProfileOwner.ME),
            ),
        )
}

class RephraseUseCase @Inject constructor(
    private val router: LlmRouter,
    private val buildContext: BuildProfileContextUseCase,
) {
    suspend operator fun invoke(text: String): Outcome<RephraseSet> =
        router.rephrase(
            RephraseRequest(
                text = text,
                partner = buildContext(ProfileOwner.PARTNER),
                me = buildContext(ProfileOwner.ME),
            ),
        )
}

/** "Before You Send": one call, both results, so the UI shows verdict and fix together. */
class BeforeYouSendUseCase @Inject constructor(
    private val analyzeTone: AnalyzeToneUseCase,
    private val rephrase: RephraseUseCase,
) {
    data class Result(val tone: ToneAnalysis, val rephrase: RephraseSet?)

    suspend operator fun invoke(text: String): Outcome<Result> {
        val toneOutcome = analyzeTone(text, authorIsMe = true)
        val tone = toneOutcome.valueOrNull ?: return toneOutcome as Outcome.Failure
        val rewrite = if (tone.isSafeToSend) null else rephrase(text).valueOrNull
        return Outcome.Success(Result(tone, rewrite))
    }
}
