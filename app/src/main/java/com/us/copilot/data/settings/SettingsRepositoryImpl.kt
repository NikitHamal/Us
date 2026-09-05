package com.us.copilot.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.us.copilot.ai.CloudEnabledSource
import com.us.copilot.data.local.crypto.SecureStore
import com.us.copilot.domain.repository.AppPreferences
import com.us.copilot.domain.repository.CloudCredentials
import com.us.copilot.domain.repository.NebiansConfig
import com.us.copilot.domain.repository.NebiansEffort
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("us_settings")

/**
 * Two-tier settings:
 * - non-sensitive toggles → DataStore
 * - credentials → [SecureStore] (EncryptedSharedPreferences, Keystore-backed)
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStore: SecureStore,
) : SettingsRepository, CloudEnabledSource {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val biometric = booleanPreferencesKey("biometric_lock")
        val cloudAi = booleanPreferencesKey("cloud_ai_enabled")
        val notificationCapture = booleanPreferencesKey("notification_capture")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val partnerConsent = booleanPreferencesKey("partner_consent")
        val lastInsightRefresh = longPreferencesKey("last_insight_refresh")
        val watchedPackages = stringSetPreferencesKey("watched_packages")
        val notificationToneCheck = booleanPreferencesKey("notification_tone_check")
        val nebiansProvider = stringPreferencesKey("nebians_provider")
        val nebiansModel = stringPreferencesKey("nebians_model")
        val nebiansEffort = stringPreferencesKey("nebians_effort")
        val nebiansTemperature = stringPreferencesKey("nebians_temperature")
        val nebiansMaxTokens = stringPreferencesKey("nebians_max_tokens")
    }

    private val credentialsState = MutableStateFlow(readCredentials())

    override val preferences: Flow<AppPreferences> =
        context.settingsDataStore.data.map { prefs ->
            AppPreferences(
                onboardingComplete = prefs[Keys.onboarding] ?: false,
                biometricLockEnabled = prefs[Keys.biometric] ?: false,
                cloudAiEnabled = prefs[Keys.cloudAi] ?: false,
                notificationCaptureEnabled = prefs[Keys.notificationCapture] ?: false,
                watchedPackages = prefs[Keys.watchedPackages] ?: emptySet(),
                notificationToneCheckEnabled = prefs[Keys.notificationToneCheck] ?: false,
                dynamicColorEnabled = prefs[Keys.dynamicColor] ?: false,
                themeMode = prefs[Keys.themeMode]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                partnerConsentRecorded = prefs[Keys.partnerConsent] ?: false,
                lastInsightRefresh = prefs[Keys.lastInsightRefresh] ?: 0L,
            )
        }.distinctUntilChanged()

    override val cloudEnabled: Flow<Boolean> = preferences.map { it.cloudAiEnabled }.distinctUntilChanged()

    override val enabled: Flow<Boolean> get() = cloudEnabled

    override val nebiansConfig: Flow<NebiansConfig> =
        context.settingsDataStore.data.map { prefs ->
            NebiansConfig(
                providerSlug = prefs[Keys.nebiansProvider]?.ifBlank { null } ?: "tryingopen",
                modelId = prefs[Keys.nebiansModel].orEmpty(),
                effort = prefs[Keys.nebiansEffort]
                    ?.let { name -> NebiansEffort.entries.firstOrNull { it.name == name } }
                    ?: NebiansEffort.BALANCED,
                temperature = prefs[Keys.nebiansTemperature]?.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.4f,
                maxTokens = prefs[Keys.nebiansMaxTokens]?.toIntOrNull()?.coerceIn(16, 8000) ?: 900,
                apiKey = secureStore.getString(SecureStore.KEY_NEBIANS_API_KEY),
                baseUrlOverride = secureStore.getString(SecureStore.KEY_NEBIANS_BASE_URL),
            )
        }.distinctUntilChanged()

    override suspend fun cloudCredentials(): CloudCredentials = readCredentials()

    override fun cloudCredentialsFlow(): Flow<CloudCredentials> = credentialsState.asStateFlow()

    override suspend fun saveCloudCredentials(credentials: CloudCredentials) {
        secureStore.putString(SecureStore.KEY_BASE_URL, credentials.baseUrl.trim())
        secureStore.putString(SecureStore.KEY_API_KEY, credentials.apiKey.trim())
        secureStore.putString(SecureStore.KEY_MODEL_NAME, credentials.modelName.trim())
        secureStore.putString(SecureStore.KEY_EMBEDDING_MODEL, credentials.embeddingModel.trim())
        credentialsState.value = readCredentials()
    }

    override suspend fun clearCloudCredentials() {
        secureStore.remove(
            SecureStore.KEY_BASE_URL,
            SecureStore.KEY_API_KEY,
            SecureStore.KEY_MODEL_NAME,
            SecureStore.KEY_EMBEDDING_MODEL,
        )
        credentialsState.value = CloudCredentials()
    }

    override suspend fun nebiansConfigSnapshot(): NebiansConfig = first(nebiansConfig)

    override suspend fun setNebiansProvider(slug: String) {
        context.settingsDataStore.edit { it[Keys.nebiansProvider] = slug.trim().lowercase() }
        // Switching provider resets the model to its default.
        context.settingsDataStore.edit { it[Keys.nebiansModel] = "" }
    }

    override suspend fun setNebiansModel(modelId: String) {
        context.settingsDataStore.edit { it[Keys.nebiansModel] = modelId.trim() }
    }

    override suspend fun setNebiansEffort(effort: NebiansEffort) {
        context.settingsDataStore.edit { it[Keys.nebiansEffort] = effort.name }
    }

    override suspend fun setNebiansTemperature(value: Float) {
        context.settingsDataStore.edit { it[Keys.nebiansTemperature] = value.coerceIn(0f, 2f).toString() }
    }

    override suspend fun setNebiansMaxTokens(value: Int) {
        context.settingsDataStore.edit { it[Keys.nebiansMaxTokens] = value.coerceIn(16, 8000).toString() }
    }

    override suspend fun saveNebiansCredentials(apiKey: String, baseUrlOverride: String) {
        secureStore.putString(SecureStore.KEY_NEBIANS_API_KEY, apiKey.trim())
        secureStore.putString(SecureStore.KEY_NEBIANS_BASE_URL, baseUrlOverride.trim())
    }

    override suspend fun clearNebiansCredentials() {
        secureStore.remove(SecureStore.KEY_NEBIANS_API_KEY, SecureStore.KEY_NEBIANS_BASE_URL)
    }

    override suspend fun setOnboardingComplete(complete: Boolean) = write(Keys.onboarding, complete)
    override suspend fun setBiometricLock(enabled: Boolean) = write(Keys.biometric, enabled)
    override suspend fun setCloudAi(enabled: Boolean) = write(Keys.cloudAi, enabled)
    override suspend fun setNotificationCapture(enabled: Boolean) = write(Keys.notificationCapture, enabled)

    override suspend fun setWatchedPackages(packages: Set<String>) =
        write(Keys.watchedPackages, packages)

    override suspend fun setNotificationToneCheck(enabled: Boolean) =
        write(Keys.notificationToneCheck, enabled)
    override suspend fun setDynamicColor(enabled: Boolean) = write(Keys.dynamicColor, enabled)
    override suspend fun setPartnerConsent(recorded: Boolean) = write(Keys.partnerConsent, recorded)

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    override suspend fun setLastInsightRefresh(timestamp: Long) {
        context.settingsDataStore.edit { it[Keys.lastInsightRefresh] = timestamp }
    }

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private fun readCredentials() = CloudCredentials(
        baseUrl = secureStore.getString(SecureStore.KEY_BASE_URL),
        apiKey = secureStore.getString(SecureStore.KEY_API_KEY),
        modelName = secureStore.getString(SecureStore.KEY_MODEL_NAME),
        embeddingModel = secureStore.getString(SecureStore.KEY_EMBEDDING_MODEL),
    )
}
