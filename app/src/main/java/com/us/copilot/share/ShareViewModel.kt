package com.us.copilot.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.core.util.TextUtils
import com.us.copilot.domain.usecase.AnalyzeToneUseCase
import com.us.copilot.domain.usecase.RephraseUseCase
import com.us.copilot.domain.usecase.SaveMemoryUseCase
import com.us.copilot.ai.model.RephraseSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val text: String = "",
    val speaker: Speaker = Speaker.PARTNER,
    val emotion: Emotion = Emotion.NEUTRAL,
    val isUnresolved: Boolean = false,
    val isAnalyzing: Boolean = false,
    val tone: ToneAnalysis? = null,
    val rephrase: RephraseSet? = null,
    val error: AppError? = null,
    val saved: Boolean = false,
    val sourcePackage: String? = null,
) {
    val hasText: Boolean get() = text.isNotBlank()
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val analyzeTone: AnalyzeToneUseCase,
    private val rephraseUseCase: RephraseUseCase,
    private val saveMemory: SaveMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    fun accept(rawText: String, sourcePackage: String?) {
        val clean = TextUtils.cleanSharedText(rawText)
        _uiState.update { it.copy(text = clean, sourcePackage = sourcePackage) }
        if (clean.isNotBlank()) analyze()
    }

    fun setSpeaker(speaker: Speaker) {
        _uiState.update { it.copy(speaker = speaker) }
        analyze()
    }

    fun setEmotion(emotion: Emotion) = _uiState.update { it.copy(emotion = emotion) }

    fun setUnresolved(value: Boolean) = _uiState.update { it.copy(isUnresolved = value) }

    fun analyze() {
        val state = _uiState.value
        if (state.text.isBlank() || state.isAnalyzing) return

        _uiState.update { it.copy(isAnalyzing = true, error = null) }
        viewModelScope.launch {
            when (val result = analyzeTone(state.text, authorIsMe = state.speaker == Speaker.ME)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        tone = result.value,
                        emotion = result.value.primaryEmotion,
                        isUnresolved = !result.value.isSafeToSend,
                    )
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isAnalyzing = false, error = result.error)
                }
            }
        }
    }

    fun rephrase() {
        val state = _uiState.value
        if (state.text.isBlank()) return
        viewModelScope.launch {
            when (val result = rephraseUseCase(state.text)) {
                is Outcome.Success -> _uiState.update { it.copy(rephrase = result.value) }
                is Outcome.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.text.isBlank()) return
        viewModelScope.launch {
            saveMemory(
                Memory(
                    text = state.text,
                    emotion = state.emotion,
                    intensity = 3,
                    timestamp = System.currentTimeMillis(),
                    source = MemorySource.SHARE,
                    speaker = state.speaker,
                    tags = state.tone?.detectedHorsemen?.map { it.horseman.label.lowercase() }.orEmpty(),
                    isUnresolved = state.isUnresolved,
                    appPackage = state.sourcePackage,
                ),
            )
            _uiState.update { it.copy(saved = true) }
        }
    }
}
