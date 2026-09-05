package com.us.copilot.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.domain.usecase.BeforeYouSendUseCase
import com.us.copilot.domain.usecase.RephraseUseCase
import com.us.copilot.domain.usecase.SaveMemoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachUiState(
    val draft: String = "",
    val isAnalyzing: Boolean = false,
    val isRephrasing: Boolean = false,
    val tone: ToneAnalysis? = null,
    val rephrase: RephraseSet? = null,
    val error: AppError? = null,
    val savedMessage: Boolean = false,
) {
    val hasInput: Boolean get() = draft.trim().length >= 2
    val hasResult: Boolean get() = tone != null
    val isBusy: Boolean get() = isAnalyzing || isRephrasing
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val beforeYouSend: BeforeYouSendUseCase,
    private val rephraseUseCase: RephraseUseCase,
    private val saveMemory: SaveMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    fun onDraftChange(value: String) = _uiState.update {
        it.copy(draft = value, tone = null, rephrase = null, error = null, savedMessage = false)
    }

    /** Prefills the coach from a share intent. */
    fun prefill(text: String) {
        _uiState.update { it.copy(draft = text) }
        analyze()
    }

    fun analyze() {
        val text = _uiState.value.draft.trim()
        if (text.length < 2 || _uiState.value.isAnalyzing) return

        _uiState.update { it.copy(isAnalyzing = true, error = null) }
        viewModelScope.launch {
            when (val result = beforeYouSend(text)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        tone = result.value.tone,
                        rephrase = result.value.rephrase ?: it.rephrase,
                    )
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isAnalyzing = false, error = result.error)
                }
            }
        }
    }

    fun rephrase() {
        val text = _uiState.value.draft.trim()
        if (text.length < 2 || _uiState.value.isRephrasing) return

        _uiState.update { it.copy(isRephrasing = true, error = null) }
        viewModelScope.launch {
            when (val result = rephraseUseCase(text)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isRephrasing = false, rephrase = result.value)
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isRephrasing = false, error = result.error)
                }
            }
        }
    }

    fun useRewrite(text: String) = _uiState.update { it.copy(draft = text) }

    /** Saves the analysed text into the timeline so it feeds the pattern engine. */
    fun saveAsMemory(speaker: Speaker = Speaker.ME, source: MemorySource = MemorySource.MANUAL) {
        val state = _uiState.value
        val text = state.draft.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            saveMemory(
                Memory(
                    text = text,
                    emotion = state.tone?.primaryEmotion ?: Emotion.NEUTRAL,
                    intensity = intensityFrom(state.tone),
                    timestamp = System.currentTimeMillis(),
                    source = source,
                    speaker = speaker,
                    tags = state.tone?.detectedHorsemen?.map { it.horseman.label.lowercase() }.orEmpty(),
                    isUnresolved = state.tone?.isSafeToSend == false,
                ),
            )
            _uiState.update { it.copy(savedMessage = true) }
        }
    }

    fun consumeSavedMessage() = _uiState.update { it.copy(savedMessage = false) }

    fun clear() = _uiState.update { CoachUiState() }

    private fun intensityFrom(tone: ToneAnalysis?): Int = when {
        tone == null -> 3
        tone.harshnessScore >= 70 -> 5
        tone.harshnessScore >= 45 -> 4
        tone.harshnessScore >= 20 -> 3
        else -> 2
    }
}
