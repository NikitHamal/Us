package com.us.copilot.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.us.copilot.MainActivity
import com.us.copilot.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the local "that one might sting" nudge. The captured text is put in the notification's
 * expanded body only — it is not persisted, and tapping simply opens the app.
 */
@Singleton
class ToneNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    init { ensureChannel() }

    fun warn(text: String, riskLabel: String) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_tone_warning_title))
            .setContentText(context.getString(R.string.notification_tone_warning_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$riskLabel\n\n${text.take(240)}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_coach),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_coach_body)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    private companion object {
        const val CHANNEL_ID = "us_tone_warnings"
        const val NOTIFICATION_ID = 4201
        const val REQUEST_CODE = 71
    }
}
