package com.us.copilot.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.us.copilot.ai.model.RiskLevel
import com.us.copilot.core.model.CapturedNotification
import com.us.copilot.core.util.TextUtils
import com.us.copilot.domain.repository.NotificationRepository
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
 * Opt-in notification capture for apps the user explicitly chose to watch.
 *
 * Guarantees, in order of how much they matter:
 *
 * 1. Does nothing unless capture is enabled AND the posting package is in the user's watch list.
 *    The watch list starts empty, so enabling the toggle alone captures nothing.
 * 2. Captured text is stored in the encrypted local database and never leaves the device here.
 * 3. Capture does NOT mean the AI can read it. Entries are stored with `sharedWithAi = false`;
 *    only an explicit user action in the history screen changes that.
 * 4. The optional tone check runs through the offline provider and only produces a local nudge.
 */
@AndroidEntryPoint
class UsNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifications: NotificationRepository
    @Inject lateinit var analyzeTone: AnalyzeToneUseCase
    @Inject lateinit var notifier: ToneNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Ignore our own notifications and anything the system marks as ongoing (media players,
        // downloads, foreground-service chrome) — none of it is conversation.
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return

        val extracted = extract(sbn) ?: return

        scope.launch {
            val prefs = settings.preferences.first()
            if (!prefs.notificationCaptureEnabled) return@launch
            if (sbn.packageName !in prefs.watchedPackages) return@launch

            val fingerprint = TextUtils.sha256("${sbn.packageName}:${extracted.title}:${extracted.text}")

            val risk = if (prefs.notificationToneCheckEnabled) {
                analyzeTone(extracted.text, authorIsMe = false).valueOrNull?.riskLevel
            } else {
                null
            }

            notifications.capture(
                CapturedNotification(
                    packageName = sbn.packageName,
                    appLabel = labelFor(sbn.packageName),
                    title = extracted.title,
                    text = extracted.text,
                    postedAt = sbn.postTime,
                    fingerprint = fingerprint,
                    sharedWithAi = false,
                    riskLevel = risk?.name,
                ),
            )

            if (risk != null && risk != RiskLevel.LOW) {
                notifier.warn(text = extracted.text, riskLabel = risk.label)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun labelFor(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

    private fun extract(sbn: StatusBarNotification): Extracted? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = (bigText ?: body)?.trim().orEmpty()
        if (text.length < MIN_LENGTH) return null
        return Extracted(title = title, text = text)
    }

    private data class Extracted(val title: String, val text: String)

    private companion object {
        /** Below this, a notification is a badge or a summary, not a message worth keeping. */
        const val MIN_LENGTH = 8
    }
}
