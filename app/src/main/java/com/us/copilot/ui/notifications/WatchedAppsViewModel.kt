package com.us.copilot.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.WatchableApp
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.notification.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchedAppsUiState(
    val apps: List<WatchableApp> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
) {
    val visible: List<WatchableApp>
        get() = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }

    val selectedCount: Int get() = apps.count { it.isWatched }
}

@HiltViewModel
class WatchedAppsViewModel @Inject constructor(
    private val provider: InstalledAppsProvider,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchedAppsUiState())
    val uiState: StateFlow<WatchedAppsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reloads the installed-app list. Exposed so the screen can refresh after the user installs
     * or removes an app without having to restart Us.
     */
    fun refresh() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val watched = settings.preferences.first().watchedPackages
        val apps = provider.load(watched)
        _uiState.update { it.copy(apps = apps, isLoading = false) }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun toggle(app: WatchableApp) = viewModelScope.launch {
        val updated = _uiState.value.apps.map {
            if (it.packageName == app.packageName) it.copy(isWatched = !it.isWatched) else it
        }
        _uiState.update { it.copy(apps = updated) }
        settings.setWatchedPackages(
            updated.filter { it.isWatched }.map { it.packageName }.toSet(),
        )
    }

    fun clearAll() = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(apps = state.apps.map { it.copy(isWatched = false) })
        }
        settings.setWatchedPackages(emptySet())
    }
}
