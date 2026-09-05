package com.us.copilot.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.AutoAwesome
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

    fun profileEdit(owner: String) = "profile/edit/$owner"
    fun memoryDetail(id: Long) = "memory/$id"
}

/** Bottom navigation entries. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    TIMELINE(Routes.TIMELINE, R.string.nav_timeline, Icons.Filled.Book, Icons.Outlined.Book),
    COACH(Routes.COACH, R.string.nav_coach, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    INSIGHTS(Routes.INSIGHTS, R.string.nav_insights, Icons.Filled.Insights, Icons.Outlined.Insights),
}
