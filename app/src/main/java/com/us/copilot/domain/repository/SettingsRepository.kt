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
    val dynamicColorEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val analyticsOptIn: Boolean = false,
    val partnerConsentRecorded: Boolean = false,
    val lastInsightRefresh: Long = 0L,
)

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

interface SettingsRepository {
    val preferences: Flow<AppPreferences>
    val cloudEnabled: Flow<Boolean>

    suspend fun cloudCredentials(): CloudCredentials
    fun cloudCredentialsFlow(): Flow<CloudCredentials>
    suspend fun saveCloudCredentials(credentials: CloudCredentials)
    suspend fun clearCloudCredentials()

    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setBiometricLock(enabled: Boolean)
    suspend fun setCloudAi(enabled: Boolean)
    suspend fun setNotificationCapture(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setPartnerConsent(recorded: Boolean)
    suspend fun setLastInsightRefresh(timestamp: Long)
}
