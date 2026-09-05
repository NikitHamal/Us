package com.us.copilot.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.MemoryTags
import com.us.copilot.core.model.Speaker
import com.us.copilot.domain.usecase.SaveMemoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JournalUiState(
    val text: String = "",
    val emotion: Emotion = Emotion.NEUTRAL,
    val intensity: Int = 3,
    val speaker: Speaker = Speaker.BOTH,
    val tags: Set<String> = emptySet(),
    val isUnresolved: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = text.trim().length >= 3 && !isSaving
    val suggestedTags: List<String> get() = MemoryTags.suggested
}

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val saveMemory: SaveMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    fun onTextChange(value: String) = _uiState.update { it.copy(text = value) }
    fun onEmotionChange(value: Emotion) = _uiState.update { it.copy(emotion = value) }
    fun onIntensityChange(value: Int) = _uiState.update { it.copy(intensity = value) }
    fun onSpeakerChange(value: Speaker) = _uiState.update { it.copy(speaker = value) }
    fun onUnresolvedChange(value: Boolean) = _uiState.update { it.copy(isUnresolved = value) }

    fun toggleTag(tag: String) = _uiState.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun prefill(text: String, source: MemorySource = MemorySource.SHARE) {
        _uiState.update { it.copy(text = text) }
        pendingSource = source
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            saveMemory(
                Memory(
                    text = state.text.trim(),
                    emotion = state.emotion,
                    intensity = state.intensity,
                    timestamp = System.currentTimeMillis(),
                    source = pendingSource,
                    speaker = state.speaker,
                    tags = state.tags.toList(),
                    isUnresolved = state.isUnresolved,
                ),
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    private var pendingSource: MemorySource = MemorySource.JOURNAL
}
