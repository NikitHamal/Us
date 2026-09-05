package com.us.copilot.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.BigFive
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.usecase.SaveProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ordered pages of the quiz. Intro pages first, then Me, then Partner. */
enum class OnboardingPage {
    WELCOME, PRIVACY, ETHICS,
    ME_NAME, ME_ATTACHMENT, ME_LOVE, ME_CONFLICT, ME_TRIGGERS, ME_SOOTHERS,
    ME_BIGFIVE, ME_STRESS, ME_COMM,
    PARTNER_NAME, PARTNER_ATTACHMENT, PARTNER_LOVE, PARTNER_CONFLICT, PARTNER_TRIGGERS,
    PARTNER_SOOTHERS, PARTNER_BIGFIVE, PARTNER_STRESS, PARTNER_COMM,
    SUMMARY;

    val isForPartner: Boolean get() = name.startsWith("PARTNER")
    val isIntro: Boolean get() = ordinal <= ETHICS.ordinal
}

data class OnboardingUiState(
    val page: OnboardingPage = OnboardingPage.WELCOME,
    val me: Profile = Profile.empty(ProfileOwner.ME),
    val partner: Profile = Profile.empty(ProfileOwner.PARTNER),
    val isSaving: Boolean = false,
    val finished: Boolean = false,
) {
    val current: Profile get() = if (page.isForPartner) partner else me
    val stepNumber: Int get() = (page.ordinal - OnboardingPage.ME_NAME.ordinal + 1).coerceAtLeast(1)
    val totalSteps: Int get() = OnboardingPage.SUMMARY.ordinal - OnboardingPage.ME_NAME.ordinal
    val canContinue: Boolean
        get() = when (page) {
            OnboardingPage.ME_NAME -> me.name.isNotBlank()
            OnboardingPage.PARTNER_NAME -> partner.name.isNotBlank()
            else -> true
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveProfile: SaveProfileUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun next() {
        val state = _uiState.value
        if (state.page == OnboardingPage.SUMMARY) {
            finish()
            return
        }
        _uiState.update { it.copy(page = OnboardingPage.entries[it.page.ordinal + 1]) }
    }

    fun back(): Boolean {
        val state = _uiState.value
        if (state.page == OnboardingPage.WELCOME) return false
        _uiState.update { it.copy(page = OnboardingPage.entries[it.page.ordinal - 1]) }
        return true
    }

    fun skipToSummary() = _uiState.update { it.copy(page = OnboardingPage.SUMMARY) }

    fun updateName(value: String) = edit { it.copy(name = value) }
    fun updateAttachment(value: AttachmentStyle) = edit { it.copy(attachmentStyle = value) }
    fun updateConflict(value: ConflictStyle) = edit { it.copy(conflictStyle = value) }
    fun updateBigFive(value: BigFive) = edit { it.copy(bigFive = value) }
    fun toggleTrigger(value: String) = edit { it.copy(triggers = it.triggers.toggle(value)) }
    fun toggleSoother(value: String) = edit { it.copy(soothers = it.soothers.toggle(value)) }
    fun toggleStress(value: String) = edit { it.copy(stressPatterns = it.stressPatterns.toggle(value)) }
    fun toggleComm(value: String) = edit { it.copy(commPreferences = it.commPreferences.toggle(value)) }

    /** Love languages are ranked: tapping appends, tapping again removes. */
    fun toggleLoveLanguage(value: LoveLanguage) = edit { profile ->
        val current = profile.loveLanguages
        profile.copy(
            loveLanguages = if (value in current) current - value else current + value,
        )
    }

    private fun finish() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val state = _uiState.value
            saveProfile(state.me)
            saveProfile(state.partner)
            settings.setOnboardingComplete(true)
            _uiState.update { it.copy(isSaving = false, finished = true) }
        }
    }

    private fun edit(transform: (Profile) -> Profile) = _uiState.update { state ->
        if (state.page.isForPartner) {
            state.copy(partner = transform(state.partner))
        } else {
            state.copy(me = transform(state.me))
        }
    }

    private fun List<String>.toggle(value: String): List<String> =
        if (value in this) this - value else this + value
}
