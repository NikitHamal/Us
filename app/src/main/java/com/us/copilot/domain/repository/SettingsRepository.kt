package com.us.copilot.domain.repository

import kotlinx.coroutines.flow.Flow

/** Credentials for any OpenAI-compatible endpoint. Never persisted in plain text. */
data class CloudCredentials(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val embeddingModel: String = "",
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    /** Joins base URL and path safely whether or not the user typed a trailing slash or /v1. */
    fun endpoint(path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return "$base/$path"
    }
}

/** Non-sensitive, user-visible preferences. */
data class AppPreferences(
    val onboardingComplete: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val cloudAiEnabled: Boolean = false,
    val notificationCaptureEnabled: Boolean = false,
    val dynamicColorEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val analyticsOptIn: Boolean = false,
    val partnerConsentRecorded: Boolean = false,
    val lastInsightRefresh: Long = 0L,
    /**
     * Packages the notification listener is allowed to read. Empty by default — enabling capture
     * does nothing until the user picks apps, so there is no implicit surveillance.
     */
    val watchedPackages: Set<String> = emptySet(),
    /** When true, newly captured notifications get an on-device tone read. Never uploads. */
    val notificationToneCheckEnabled: Boolean = false,
)

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

/** Reasoning effort for TryingOpen-style providers (ported from Nebians). */
enum class NebiansEffort(val label: String) { QUICK("Quick"), BALANCED("Balanced"), DEEP("Deep") }

/**
 * Which Nebians model answers cloud requests.
 *
 * Non-sensitive parts live in DataStore; [apiKey]/[baseUrlOverride] live in encrypted storage.
 * An empty [modelId] means the provider default. [effort] only applies to effort-style
 * providers (TryingOpen); [temperature]/[maxTokens] only to temperature-style ones.
 */
data class NebiansConfig(
    val providerSlug: String = "tryingopen",
    val modelId: String = "",
    val effort: NebiansEffort = NebiansEffort.BALANCED,
    val temperature: Float = 0.4f,
    val maxTokens: Int = 900,
    val apiKey: String = "",
    val baseUrlOverride: String = "",
)

interface SettingsRepository {
    val preferences: Flow<AppPreferences>
    val cloudEnabled: Flow<Boolean>
    val nebiansConfig: Flow<NebiansConfig>

    suspend fun cloudCredentials(): CloudCredentials
    fun cloudCredentialsFlow(): Flow<CloudCredentials>
    suspend fun saveCloudCredentials(credentials: CloudCredentials)
    suspend fun clearCloudCredentials()

    suspend fun nebiansConfigSnapshot(): NebiansConfig
    suspend fun setNebiansProvider(slug: String)
    suspend fun setNebiansModel(modelId: String)
    suspend fun setNebiansEffort(effort: NebiansEffort)
    suspend fun setNebiansTemperature(value: Float)
    suspend fun setNebiansMaxTokens(value: Int)
    suspend fun saveNebiansCredentials(apiKey: String, baseUrlOverride: String)
    suspend fun clearNebiansCredentials()

    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setBiometricLock(enabled: Boolean)
    suspend fun setCloudAi(enabled: Boolean)
    suspend fun setNotificationCapture(enabled: Boolean)
    suspend fun setWatchedPackages(packages: Set<String>)
    suspend fun setNotificationToneCheck(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setPartnerConsent(recorded: Boolean)
    suspend fun setLastInsightRefresh(timestamp: Long)
}
