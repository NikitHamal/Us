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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies Nebians-first routing with real errors and no offline fallback. */
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
        cloudUsable: Boolean,
        cloudResult: Outcome<ToneAnalysis> = Outcome.Success(tone(0.95f, ProviderId.CLOUD)),
        nebiansResult: Outcome<ToneAnalysis> = Outcome.Success(tone(0.9f, ProviderId.NEBIANS)),
    ): LlmRouter {
        val offline = mockk<LlmProvider>()
        val nebians = mockk<LlmProvider>()
        val cloud = mockk<LlmProvider>()
        coEvery { nebians.analyzeTone(any()) } returns nebiansResult
        coEvery { cloud.analyzeTone(any()) } returns cloudResult
        val gate = object : CloudGate {
            override suspend fun isCloudUsable(): Boolean = cloudUsable
        }
        return LlmRouter(offline, nebians, cloud, gate)
    }

    @Test
    fun `nebians answers first`() = runTest {
        val result = router(cloudUsable = true).analyzeTone(request)
        assertEquals(ProviderId.NEBIANS, result.valueOrNull?.provider)
    }

    @Test
    fun `nebians failure falls through to legacy cloud`() = runTest {
        val result = router(
            cloudUsable = true,
            nebiansResult = Outcome.Failure(AppError.NoNetwork),
        ).analyzeTone(request)
        assertEquals(ProviderId.CLOUD, result.valueOrNull?.provider)
    }

    @Test
    fun `misconfigured nebians still reaches legacy cloud`() = runTest {
        val result = router(
            cloudUsable = true,
            nebiansResult = Outcome.Failure(AppError.MissingCredentials),
        ).analyzeTone(request)
        assertEquals(ProviderId.CLOUD, result.valueOrNull?.provider)
    }

    @Test
    fun `total failure surfaces the nebians error, never offline`() = runTest {
        val result = router(
            cloudUsable = true,
            nebiansResult = Outcome.Failure(AppError.NoNetwork),
            cloudResult = Outcome.Failure(AppError.NoNetwork),
        ).analyzeTone(request)
        assertTrue(result is Outcome.Failure)
        assertEquals(AppError.NoNetwork, (result as Outcome.Failure).error)
    }

    @Test
    fun `no fallback hides misconfiguration`() = runTest {
        val result = router(
            cloudUsable = false,
            nebiansResult = Outcome.Failure(AppError.MissingCredentials),
        ).analyzeTone(request)
        assertTrue(result is Outcome.Failure)
        assertEquals(AppError.MissingCredentials, (result as Outcome.Failure).error)
    }
}
