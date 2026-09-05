package com.us.copilot.ai

import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.ProviderId
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the offline-first escalation policy in [LlmRouter]. */
class LlmRouterTest {

    private val request = ToneRequest(text = "any message")

    private fun tone(confidence: Float, provider: ProviderId) = ToneAnalysis(
        sentiment = 0f,
        primaryEmotion = Emotion.NEUTRAL,
        harshnessScore = 10,
        summary = "",
        confidence = confidence,
        provider = provider,
    )

    private fun router(
        offlineConfidence: Float,
        cloudUsable: Boolean,
        cloudResult: Outcome<ToneAnalysis> = Outcome.Success(tone(0.95f, ProviderId.CLOUD)),
    ): LlmRouter {
        val offline = mockk<LlmProvider>()
        val cloud = mockk<LlmProvider>()
        coEvery { offline.analyzeTone(any()) } returns
            Outcome.Success(tone(offlineConfidence, ProviderId.OFFLINE))
        coEvery { cloud.analyzeTone(any()) } returns cloudResult
        val gate = object : CloudGate {
            override suspend fun isCloudUsable(): Boolean = cloudUsable
        }
        return LlmRouter(offline, cloud, gate)
    }

    @Test
    fun `high offline confidence never escalates`() = runTest {
        val result = router(offlineConfidence = 0.85f, cloudUsable = true).analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }

    @Test
    fun `low confidence escalates when cloud is enabled`() = runTest {
        val result = router(offlineConfidence = 0.4f, cloudUsable = true).analyzeTone(request)
        assertEquals(ProviderId.CLOUD, result.valueOrNull?.provider)
    }

    @Test
    fun `low confidence stays offline when cloud is disabled`() = runTest {
        val result = router(offlineConfidence = 0.4f, cloudUsable = false).analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }

    @Test
    fun `cloud failure falls back to the offline answer`() = runTest {
        val result = router(
            offlineConfidence = 0.3f,
            cloudUsable = true,
            cloudResult = Outcome.Failure(AppError.NoNetwork),
        ).analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }

    @Test
    fun `threshold boundary does not escalate`() = runTest {
        val result = router(offlineConfidence = CONFIDENCE_THRESHOLD, cloudUsable = true)
            .analyzeTone(request)
        assertEquals(ProviderId.OFFLINE, result.valueOrNull?.provider)
    }
}
