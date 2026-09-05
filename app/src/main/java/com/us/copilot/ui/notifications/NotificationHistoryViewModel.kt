package com.us.copilot.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.CapturedNotification
import com.us.copilot.domain.repository.NotificationRepository
import com.us.copilot.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationHistoryUiState(
    val items: List<CapturedNotification> = emptyList(),
    val isLoading: Boolean = true,
    val captureEnabled: Boolean = false,
    val watchedCount: Int = 0,
    val filter: HistoryFilter = HistoryFilter.ALL,
) {
    val visible: List<CapturedNotification>
        get() = when (filter) {
            HistoryFilter.ALL -> items
            HistoryFilter.SHARED -> items.filter { it.sharedWithAi }
        }

    val sharedCount: Int get() = items.count { it.sharedWithAi }
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

enum class HistoryFilter { ALL, SHARED }

@HiltViewModel
class NotificationHistoryViewModel @Inject constructor(
    private val repository: NotificationRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NotificationHistoryUiState> =
        combine(
            repository.observeAll(),
            settings.preferences,
            filter,
        ) { items, prefs, currentFilter ->
            NotificationHistoryUiState(
                items = items,
                isLoading = false,
                captureEnabled = prefs.notificationCaptureEnabled,
                watchedCount = prefs.watchedPackages.size,
                filter = currentFilter,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationHistoryUiState(),
        )

    fun setFilter(value: HistoryFilter) = filter.update { value }

    /** The only path by which captured text becomes readable to the AI. */
    fun toggleShared(item: CapturedNotification) = viewModelScope.launch {
        repository.setSharedWithAi(item.id, !item.sharedWithAi)
    }

    fun stopSharingAll() = viewModelScope.launch { repository.stopSharingAll() }

    fun delete(item: CapturedNotification) = viewModelScope.launch {
        repository.delete(item.id)
    }

    fun clearAll() = viewModelScope.launch { repository.clearAll() }
}
