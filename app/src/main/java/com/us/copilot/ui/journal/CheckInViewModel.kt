package com.us.copilot.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.domain.usecase.ObserveTodayCheckInUseCase
import com.us.copilot.domain.usecase.SaveCheckInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckInUiState(
    val mood: Int = 3,
    val energy: Int = 3,
    val connection: Int = 3,
    val note: String = "",
    val gratitude: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val saveCheckIn: SaveCheckInUseCase,
    observeToday: ObserveTodayCheckInUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = observeToday().first()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    mood = existing?.mood ?: it.mood,
                    energy = existing?.energy ?: it.energy,
                    connection = existing?.connection ?: it.connection,
                    note = existing?.note ?: it.note,
                    gratitude = existing?.gratitude ?: it.gratitude,
                )
            }
        }
    }

    fun onMood(value: Int) = _uiState.update { it.copy(mood = value) }
    fun onEnergy(value: Int) = _uiState.update { it.copy(energy = value) }
    fun onConnection(value: Int) = _uiState.update { it.copy(connection = value) }
    fun onNote(value: String) = _uiState.update { it.copy(note = value) }
    fun onGratitude(value: String) = _uiState.update { it.copy(gratitude = value) }

    fun save() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val state = _uiState.value
            saveCheckIn(
                CheckIn(
                    epochDay = TimeUtils.epochDay(System.currentTimeMillis()),
                    mood = state.mood,
                    energy = state.energy,
                    connection = state.connection,
                    note = state.note.trim(),
                    gratitude = state.gratitude.trim(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
