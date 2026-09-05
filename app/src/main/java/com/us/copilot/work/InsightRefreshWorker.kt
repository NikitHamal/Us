package com.us.copilot.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.usecase.BuildInsightsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recomputes the deterministic insight summary in the background so the dashboard opens instantly.
 * Runs on-device only; it never requires network.
 */
@HiltWorker
class InsightRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val buildInsights: BuildInsightsUseCase,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        buildInsights()
        settings.setLastInsightRefresh(System.currentTimeMillis())
        Result.success()
    }.getOrElse { Result.retry() }

    companion object { const val UNIQUE_NAME = "us_insight_refresh" }
}

@Singleton
class InsightScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<InsightRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            InsightRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
