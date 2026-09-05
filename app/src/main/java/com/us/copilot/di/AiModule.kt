package com.us.copilot.di

import com.us.copilot.ai.CloudEnabledSource
import com.us.copilot.ai.CloudGate
import com.us.copilot.ai.LlmProvider
import com.us.copilot.ai.LlmRouter
import com.us.copilot.ai.cloud.CloudProvider
import com.us.copilot.ai.offline.OfflineProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class Offline
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class Cloud

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    explicitNulls = false
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

    @Provides @Singleton @Offline
    fun provideOfflineProvider(provider: OfflineProvider): LlmProvider = provider

    @Provides @Singleton @Cloud
    fun provideCloudProvider(provider: CloudProvider): LlmProvider = provider

    @Provides
    @Singleton
    fun provideCloudGate(
        source: CloudEnabledSource,
        @Cloud cloud: LlmProvider,
    ): CloudGate = object : CloudGate {
        override suspend fun isCloudUsable(): Boolean = source.enabled.first() && cloud.isAvailable()
    }

    @Provides
    @Singleton
    fun provideLlmRouter(
        @Offline offline: LlmProvider,
        @Cloud cloud: LlmProvider,
        gate: CloudGate,
    ): LlmRouter = LlmRouter(offline, cloud, gate)
}
