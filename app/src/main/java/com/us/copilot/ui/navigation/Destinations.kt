package com.us.copilot.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import com.us.copilot.R

/** Every navigable route in the app, in one place. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TIMELINE = "timeline"
    const val COACH = "coach"
    const val JOURNAL = "journal"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val PROFILE_ME = "profile/me"
    const val PROFILE_PARTNER = "profile/partner"
    const val PROFILE_EDIT = "profile/edit/{owner}"
    const val MEMORY_DETAIL = "memory/{memoryId}"
    const val CHECK_IN = "check_in"
    const val NOTIFICATION_HISTORY = "notifications"
    const val WATCHED_APPS = "notifications/apps"

    fun profileEdit(owner: String) = "profile/edit/$owner"
    fun memoryDetail(id: Long) = "memory/$id"
}

/**
 * Bottom navigation entries — deliberately three.
 *
 * Coach is intentionally absent: it is a focused, full-screen task with its own keyboard and
 * transcript, so it is reached from Today's app bar and pushed as a standalone destination
 * rather than living behind a tab that keeps the nav bar overlapping its composer.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    TIMELINE(Routes.TIMELINE, R.string.nav_timeline, Icons.Filled.Book, Icons.Outlined.Book),
    INSIGHTS(Routes.INSIGHTS, R.string.nav_insights, Icons.Filled.Insights, Icons.Outlined.Insights),
}
