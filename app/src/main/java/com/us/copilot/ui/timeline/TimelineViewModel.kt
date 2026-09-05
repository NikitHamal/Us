package com.us.copilot.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemoryFilter
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.domain.usecase.DeleteMemoryUseCase
import com.us.copilot.domain.usecase.ObserveMemoriesUseCase
import com.us.copilot.domain.usecase.ObserveTagsUseCase
import com.us.copilot.domain.usecase.ToggleResolvedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val isLoading: Boolean = true,
    val memories: List<Memory> = emptyList(),
    val filter: MemoryFilter = MemoryFilter(),
    val availableTags: List<String> = emptyList(),
    val showFilterSheet: Boolean = false,
) {
    val isEmpty: Boolean get() = memories.isEmpty()
    val isFiltered: Boolean get() = filter.isActive
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    observeMemories: ObserveMemoriesUseCase,
    observeTags: ObserveTagsUseCase,
    private val toggleResolved: ToggleResolvedUseCase,
    private val deleteMemory: DeleteMemoryUseCase,
) : ViewModel() {

    private val filterState = MutableStateFlow(MemoryFilter())
    private val sheetState = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<TimelineUiState> = combine(
        filterState.debounce { if (it.query.isBlank()) 0L else 220L }
            .flatMapLatest { filter -> observeMemories(filter) },
        filterState,
        observeTags(),
        sheetState,
    ) { memories, filter, tags, showSheet ->
        TimelineUiState(
            isLoading = false,
            memories = memories,
            filter = filter,
            availableTags = tags,
            showFilterSheet = showSheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun onQueryChange(query: String) = filterState.update { it.copy(query = query) }

    fun toggleEmotion(emotion: Emotion) = filterState.update {
        it.copy(emotions = it.emotions.toggle(emotion))
    }

    fun toggleSource(source: MemorySource) = filterState.update {
        it.copy(sources = it.sources.toggle(source))
    }

    fun toggleSpeaker(speaker: Speaker) = filterState.update {
        it.copy(speakers = it.speakers.toggle(speaker))
    }

    fun toggleUnresolvedOnly() = filterState.update { it.copy(onlyUnresolved = !it.onlyUnresolved) }

    fun selectTag(tag: String?) = filterState.update { it.copy(tag = tag) }

    fun clearFilters() = filterState.update { MemoryFilter(query = it.query) }

    fun setFilterSheetVisible(visible: Boolean) { sheetState.value = visible }

    fun setResolved(id: Long, resolved: Boolean) {
        viewModelScope.launch { toggleResolved(id, resolved) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { deleteMemory(id) }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value
}
