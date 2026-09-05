package com.us.copilot.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.BigFive
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.domain.usecase.ObserveProfileHistoryUseCase
import com.us.copilot.domain.usecase.ObserveProfileUseCase
import com.us.copilot.domain.usecase.RestoreProfileVersionUseCase
import com.us.copilot.domain.usecase.SaveProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val owner: ProfileOwner = ProfileOwner.ME,
    val profile: Profile? = null,
    val history: List<Profile> = emptyList(),
    val draft: Profile = Profile.empty(ProfileOwner.ME),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val isEmpty: Boolean get() = profile == null
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeProfile: ObserveProfileUseCase,
    private val observeHistory: ObserveProfileHistoryUseCase,
    private val saveProfile: SaveProfileUseCase,
    private val restoreVersion: RestoreProfileVersionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var initialised = false

    fun load(owner: ProfileOwner) {
        if (initialised && _uiState.value.owner == owner) return
        initialised = true
        _uiState.update { it.copy(owner = owner, isLoading = true) }

        viewModelScope.launch {
            observeProfile(owner).collect { profile ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        profile = profile,
                        draft = if (state.isSaving) state.draft else profile ?: Profile.empty(owner),
                    )
                }
            }
        }
        viewModelScope.launch {
            observeHistory(owner).collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun editName(value: String) = editDraft { it.copy(name = value) }
    fun editAttachment(value: AttachmentStyle) = editDraft { it.copy(attachmentStyle = value) }
    fun editConflict(value: ConflictStyle) = editDraft { it.copy(conflictStyle = value) }
    fun editBigFive(value: BigFive) = editDraft { it.copy(bigFive = value) }
    fun editNote(value: String) = editDraft { it.copy(note = value) }

    fun toggleLoveLanguage(value: LoveLanguage) = editDraft { profile ->
        val current = profile.loveLanguages
        profile.copy(loveLanguages = if (value in current) current - value else current + value)
    }

    fun toggleTrigger(value: String) = editDraft { it.copy(triggers = it.triggers.toggle(value)) }
    fun toggleSoother(value: String) = editDraft { it.copy(soothers = it.soothers.toggle(value)) }
    fun toggleStress(value: String) = editDraft { it.copy(stressPatterns = it.stressPatterns.toggle(value)) }
    fun toggleComm(value: String) = editDraft { it.copy(commPreferences = it.commPreferences.toggle(value)) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            saveProfile(state.draft.copy(owner = state.owner))
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            restoreVersion(id)
            val refreshed = observeProfile(_uiState.value.owner).first()
            _uiState.update { it.copy(profile = refreshed, draft = refreshed ?: it.draft) }
        }
    }

    fun consumeSaved() = _uiState.update { it.copy(saved = false) }

    private fun editDraft(transform: (Profile) -> Profile) =
        _uiState.update { it.copy(draft = transform(it.draft)) }

    private fun List<String>.toggle(value: String): List<String> =
        if (value in this) this - value else this + value
}
