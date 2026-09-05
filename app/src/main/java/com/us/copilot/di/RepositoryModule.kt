package com.us.copilot.di

import com.us.copilot.ai.CloudEnabledSource
import com.us.copilot.data.repository.CheckInRepositoryImpl
import com.us.copilot.data.repository.MemoryRepositoryImpl
import com.us.copilot.data.repository.ProfileRepositoryImpl
import com.us.copilot.data.settings.SettingsRepositoryImpl
import com.us.copilot.domain.repository.CheckInRepository
import com.us.copilot.domain.repository.MemoryRepository
import com.us.copilot.domain.repository.ProfileRepository
import com.us.copilot.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds @Singleton
    abstract fun bindCheckInRepository(impl: CheckInRepositoryImpl): CheckInRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindCloudEnabledSource(impl: SettingsRepositoryImpl): CloudEnabledSource
}
