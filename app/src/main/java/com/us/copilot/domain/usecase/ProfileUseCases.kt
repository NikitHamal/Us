package com.us.copilot.domain.usecase

import com.us.copilot.ai.model.ProfileContext
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveProfileUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    operator fun invoke(owner: ProfileOwner): Flow<Profile?> = repository.observeProfile(owner)
}

class SaveProfileUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(profile: Profile): Long = repository.saveNewVersion(profile)
}

class ObserveProfileHistoryUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    operator fun invoke(owner: ProfileOwner): Flow<List<Profile>> = repository.observeHistory(owner)
}

class RestoreProfileVersionUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(id: Long) = repository.restoreVersion(id)
}

/** Builds the compact context object handed to any [com.us.copilot.ai.LlmProvider]. */
class BuildProfileContextUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(owner: ProfileOwner): ProfileContext =
        repository.get(owner)?.toContext() ?: ProfileContext.Empty
}

fun Profile.toContext(): ProfileContext = ProfileContext(
    name = name,
    attachmentStyle = attachmentStyle.label,
    loveLanguages = loveLanguages.map { it.name },
    conflictStyle = conflictStyle.label,
    triggers = triggers,
    soothers = soothers,
    commPreferences = commPreferences,
)
