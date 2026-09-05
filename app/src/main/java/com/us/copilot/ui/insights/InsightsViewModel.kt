package com.us.copilot.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.ai.model.PatternReport
import com.us.copilot.core.model.InsightSummary
import com.us.copilot.core.util.AppError
import com.us.copilot.core.util.Outcome
import com.us.copilot.domain.usecase.BuildInsightsUseCase
import com.us.copilot.domain.usecase.ExtractPatternsUseCase
import com.us.copilot.pattern.PatternEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val summary: InsightSummary = InsightSummary(),
    val cadence: PatternEngine.Cadence = PatternEngine.Cadence(0f, null, 0),
    val report: PatternReport? = null,
    val error: AppError? = null,
) {
    val isEmpty: Boolean get() = !summary.hasData
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val buildInsights: BuildInsightsUseCase,
    private val extractPatterns: ExtractPatternsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val insights = buildInsights()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    summary = insights.summary,
                    cadence = insights.cadence,
                )
            }
            if (insights.summary.hasData) analysePatterns()
        }
    }

    fun analysePatterns() {
        if (_uiState.value.isAnalyzing) return
        _uiState.update { it.copy(isAnalyzing = true) }
        viewModelScope.launch {
            when (val result = extractPatterns()) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isAnalyzing = false, report = result.value)
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isAnalyzing = false, error = result.error)
                }
            }
        }
    }
}
