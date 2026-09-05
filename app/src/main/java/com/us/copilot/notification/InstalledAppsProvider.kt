package com.us.copilot.notification

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.us.copilot.core.model.WatchableApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists apps the user can choose to watch for notifications.
 *
 * Only launchable apps are offered — services and system plumbing never post the kind of
 * conversational notification this feature is about, and hiding them keeps the picker short.
 * Our own package is excluded so the app cannot watch itself.
 *
 * Messaging apps are surfaced first because that is overwhelmingly what people want to watch,
 * but nothing is preselected: the watch list starts empty and stays that way until the user acts.
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(watched: Set<String>): List<WatchableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION", "QueryPermissionsNeeded")
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                WatchableApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isWatched = info.packageName in watched,
                )
            }
            .sortedWith(
                compareByDescending<WatchableApp> { it.isWatched }
                    .thenByDescending { it.packageName in SUGGESTED }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    companion object {
        /** Sorted to the top of the picker as a convenience. Never auto-enabled. */
        val SUGGESTED = setOf(
            "com.instagram.android",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.snapchat.android",
            "com.google.android.apps.messaging",
            "com.discord",
        )
    }
}
