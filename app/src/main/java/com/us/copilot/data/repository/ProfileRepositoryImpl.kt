package com.us.copilot.data.repository

import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.data.local.dao.ProfileDao
import com.us.copilot.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao,
) : ProfileRepository {

    override fun observeProfile(owner: ProfileOwner): Flow<Profile?> =
        dao.observeActive(owner.name).map { it?.toDomain() }

    override fun observeAll(): Flow<List<Profile>> =
        dao.observeAllActive().map { list -> list.map { it.toDomain() } }

    override suspend fun get(owner: ProfileOwner): Profile? = dao.getActive(owner.name)?.toDomain()

    override suspend fun saveNewVersion(profile: Profile): Long =
        dao.insertNewVersion(
            profile.copy(id = 0L, updatedAt = System.currentTimeMillis()).toEntity(),
        )

    override fun observeHistory(owner: ProfileOwner): Flow<List<Profile>> =
        dao.observeHistory(owner.name).map { list -> list.map { it.toDomain() } }

    override suspend fun restoreVersion(id: Long) = dao.restore(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
