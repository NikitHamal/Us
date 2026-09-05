package com.us.copilot.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.R
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.repository.ThemeMode
import com.us.copilot.security.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isReady: Boolean = false,
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val lockEnabled: Boolean = false,
    val isUnlocked: Boolean = false,
) {
    val showLockScreen: Boolean get() = lockEnabled && !isUnlocked
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Drives the splash screen: keep it up until preferences have been read once. */
    val isReady: StateFlow<Boolean> = _uiState
        .map { it.isReady }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        isReady = true,
                        onboardingComplete = prefs.onboardingComplete,
                        themeMode = prefs.themeMode,
                        dynamicColor = prefs.dynamicColorEnabled,
                        lockEnabled = prefs.biometricLockEnabled,
                        isUnlocked = if (prefs.biometricLockEnabled) current.isUnlocked else true,
                    )
                }
            }
        }
    }

    fun unlock(activity: FragmentActivity) {
        viewModelScope.launch {
            val granted = BiometricGate.authenticate(
                activity = activity,
                title = activity.getString(R.string.lock_prompt_title),
                subtitle = activity.getString(R.string.lock_prompt_subtitle),
            )
            if (granted) _uiState.update { it.copy(isUnlocked = true) }
        }
    }

    /** Re-lock as soon as the app leaves the foreground. */
    fun onAppBackgrounded() {
        if (_uiState.value.lockEnabled) _uiState.update { it.copy(isUnlocked = false) }
    }
}
