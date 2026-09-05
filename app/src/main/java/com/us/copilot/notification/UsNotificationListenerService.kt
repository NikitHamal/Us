package com.us.copilot.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.us.copilot.ai.model.RiskLevel
import com.us.copilot.core.util.TextUtils
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.usecase.AnalyzeToneUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opt-in, on-device tone check for Instagram and Messenger notifications.
 *
 * Guarantees:
 * - Does nothing at all unless the user turned the feature on in Settings.
 * - Only looks at an explicit package allow-list.
 * - Analysis runs through the offline provider path; text is never uploaded and never stored
 *   automatically. The user is only shown a local nudge.
 */
@AndroidEntryPoint
class UsNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var analyzeTone: AnalyzeToneUseCase
    @Inject lateinit var notifier: ToneNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recentlySeen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WATCHED_PACKAGES) return

        val text = extractText(sbn) ?: return
        if (text.length < MIN_LENGTH) return

        val fingerprint = TextUtils.sha256("${sbn.packageName}:$text")
        synchronized(recentlySeen) {
            if (!recentlySeen.add(fingerprint)) return
            if (recentlySeen.size > DEDUPE_WINDOW) {
                recentlySeen.iterator().let { iterator ->
                    iterator.next()
                    iterator.remove()
                }
            }
        }

        scope.launch {
            if (!settings.preferences.first().notificationCaptureEnabled) return@launch

            val tone = analyzeTone(text, authorIsMe = false).valueOrNull ?: return@launch
            if (tone.riskLevel != RiskLevel.LOW) {
                notifier.warn(text = text, riskLabel = tone.riskLevel.label)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun extractText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        return (bigText ?: body)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        val WATCHED_PACKAGES = setOf(
            "com.instagram.android",
            "com.facebook.orca",
            "com.facebook.mlite",
        )
        const val MIN_LENGTH = 12
        const val DEDUPE_WINDOW = 50
    }
}
