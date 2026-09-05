package com.us.copilot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.ai.LlmProvider
import com.us.copilot.ai.model.ToneRequest
import com.us.copilot.ai.offline.CactModelLoader
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.di.Cloud
import com.us.copilot.domain.repository.AppPreferences
import com.us.copilot.domain.repository.CloudCredentials
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.repository.ThemeMode
import com.us.copilot.domain.usecase.WipeAllDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data object Success : ConnectionTest
    data class Failed(val error: AppError) : ConnectionTest
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val prefs: AppPreferences = AppPreferences(),
    val credentials: CloudCredentials = CloudCredentials(),
    val modelState: String = "",
    val connectionTest: ConnectionTest = ConnectionTest.Idle,
    val showWipeDialog: Boolean = false,
    val snackbar: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val wipeAllData: WipeAllDataUseCase,
    private val modelLoader: CactModelLoader,
    @Cloud private val cloudProvider: LlmProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _uiState.update { it.copy(isLoading = false, prefs = prefs) }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(credentials = settings.cloudCredentials()) }
        }
        viewModelScope.launch {
            val state = when (val loaded = modelLoader.state()) {
                is CactModelLoader.ModelState.Ready -> "${loaded.sizeBytes / 1024} KB"
                is CactModelLoader.ModelState.Failed -> loaded.reason
                CactModelLoader.ModelState.RulesOnly -> ""
            }
            _uiState.update { it.copy(modelState = state) }
        }
    }

    fun setBiometric(enabled: Boolean) = launchSetting { settings.setBiometricLock(enabled) }
    fun setCloudAi(enabled: Boolean) = launchSetting { settings.setCloudAi(enabled) }
    fun setNotificationCapture(enabled: Boolean) = launchSetting { settings.setNotificationCapture(enabled) }

    fun setNotificationToneCheck(enabled: Boolean) =
        launchSetting { settings.setNotificationToneCheck(enabled) }
    fun setDynamicColor(enabled: Boolean) = launchSetting { settings.setDynamicColor(enabled) }
    fun setThemeMode(mode: ThemeMode) = launchSetting { settings.setThemeMode(mode) }

    fun editCredentials(transform: (CloudCredentials) -> CloudCredentials) =
        _uiState.update { it.copy(credentials = transform(it.credentials)) }

    fun saveCredentials(savedMessage: String) {
        viewModelScope.launch {
            settings.saveCloudCredentials(_uiState.value.credentials)
            _uiState.update { it.copy(snackbar = savedMessage) }
        }
    }

    fun testConnection() {
        if (_uiState.value.connectionTest is ConnectionTest.Running) return
        _uiState.update { it.copy(connectionTest = ConnectionTest.Running) }
        viewModelScope.launch {
            settings.saveCloudCredentials(_uiState.value.credentials)
            val result = cloudProvider.analyzeTone(
                ToneRequest(text = "Hey, are we okay? I have been thinking about last night."),
            )
            _uiState.update {
                it.copy(
                    connectionTest = when (result) {
                        is Outcome.Success -> ConnectionTest.Success
                        is Outcome.Failure -> ConnectionTest.Failed(result.error)
                    },
                )
            }
        }
    }

    fun setWipeDialogVisible(visible: Boolean) = _uiState.update { it.copy(showWipeDialog = visible) }

    fun wipeEverything(confirmationMessage: String) {
        viewModelScope.launch {
            wipeAllData()
            settings.clearCloudCredentials()
            settings.setOnboardingComplete(false)
            _uiState.update {
                it.copy(
                    showWipeDialog = false,
                    credentials = CloudCredentials(),
                    snackbar = confirmationMessage,
                )
            }
        }
    }

    fun consumeSnackbar() = _uiState.update { it.copy(snackbar = null) }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
