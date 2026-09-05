package com.us.copilot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.CheckIn
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.domain.repository.CheckInRepository
import com.us.copilot.domain.usecase.GetRepairStartersUseCase
import com.us.copilot.domain.usecase.ObserveProfileUseCase
import com.us.copilot.domain.usecase.ObserveRecentMemoriesUseCase
import com.us.copilot.domain.usecase.ObserveTodayCheckInUseCase
import com.us.copilot.domain.usecase.ObserveUnresolvedUseCase
import com.us.copilot.domain.usecase.RepairStarter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val me: Profile? = null,
    val partner: Profile? = null,
    val todayCheckIn: CheckIn? = null,
    val recent: List<Memory> = emptyList(),
    val unresolved: List<Memory> = emptyList(),
    val streakDays: Int = 0,
    val repairStarters: List<RepairStarter> = emptyList(),
) {
    val isEmpty: Boolean get() = recent.isEmpty() && todayCheckIn == null
    val hasCheckedInToday: Boolean get() = todayCheckIn != null
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeProfile: ObserveProfileUseCase,
    observeRecent: ObserveRecentMemoriesUseCase,
    observeUnresolved: ObserveUnresolvedUseCase,
    observeTodayCheckIn: ObserveTodayCheckInUseCase,
    private val checkIns: CheckInRepository,
    private val repairStarters: GetRepairStartersUseCase,
) : ViewModel() {

    private val streak = MutableStateFlow(0)
    private val starters = MutableStateFlow<List<RepairStarter>>(emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        observeProfile(ProfileOwner.ME),
        observeProfile(ProfileOwner.PARTNER),
        observeRecent(limit = 5),
        observeUnresolved(),
        observeTodayCheckIn(),
    ) { me, partner, recent, unresolved, today ->
        HomeUiState(
            isLoading = false,
            me = me,
            partner = partner,
            todayCheckIn = today,
            recent = recent,
            unresolved = unresolved,
        )
    }.combine(streak) { state, days -> state.copy(streakDays = days) }
        .combine(starters) { state, list ->
            state.copy(repairStarters = list.ifEmpty { repairStarters(state.partner) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            val days = checkIns.allDays()
            streak.value = TimeUtils.streak(days, TimeUtils.epochDay(System.currentTimeMillis()))
        }
    }

    fun shuffleRepairStarters(partner: Profile?) {
        starters.value = repairStarters(partner)
    }
}
