package com.us.copilot.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.ui.coach.CoachScreen
import com.us.copilot.ui.insights.InsightsScreen
import com.us.copilot.ui.journal.CheckInScreen
import com.us.copilot.ui.journal.JournalScreen
import com.us.copilot.ui.home.HomeScreen
import com.us.copilot.ui.onboarding.OnboardingScreen
import com.us.copilot.ui.profile.ProfileEditScreen
import com.us.copilot.ui.profile.ProfileScreen
import com.us.copilot.ui.settings.SettingsScreen
import com.us.copilot.ui.timeline.TimelineScreen

private const val MOTION_MS = 320

@Composable
fun UsNavHost(
    navController: NavHostController,
    startDestination: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(MOTION_MS)) { it / 6 } + fadeIn(tween(MOTION_MS))
        },
        exitTransition = { fadeOut(tween(MOTION_MS / 2)) },
        popEnterTransition = { fadeIn(tween(MOTION_MS)) },
        popExitTransition = {
            slideOutHorizontally(tween(MOTION_MS)) { it / 6 } + fadeOut(tween(MOTION_MS))
        },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                contentPadding = contentPadding,
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                contentPadding = contentPadding,
                onOpenCoach = { navController.navigate(Routes.COACH) },
                onOpenJournal = { navController.navigate(Routes.JOURNAL) },
                onOpenCheckIn = { navController.navigate(Routes.CHECK_IN) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenProfile = { owner ->
                    navController.navigate(
                        if (owner == ProfileOwner.ME) Routes.PROFILE_ME else Routes.PROFILE_PARTNER,
                    )
                },
                onOpenTimeline = { navController.navigate(Routes.TIMELINE) },
            )
        }

        composable(Routes.TIMELINE) {
            TimelineScreen(
                contentPadding = contentPadding,
                onAddMoment = { navController.navigate(Routes.JOURNAL) },
            )
        }

        composable(Routes.COACH) {
            CoachScreen(contentPadding = contentPadding)
        }

        composable(Routes.INSIGHTS) {
            InsightsScreen(contentPadding = contentPadding)
        }

        composable(Routes.JOURNAL) {
            JournalScreen(
                contentPadding = contentPadding,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CHECK_IN) {
            CheckInScreen(
                contentPadding = contentPadding,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROFILE_ME) {
            ProfileScreen(
                owner = ProfileOwner.ME,
                contentPadding = contentPadding,
                onEdit = { navController.navigate(Routes.profileEdit(ProfileOwner.ME.name)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PROFILE_PARTNER) {
            ProfileScreen(
                owner = ProfileOwner.PARTNER,
                contentPadding = contentPadding,
                onEdit = { navController.navigate(Routes.profileEdit(ProfileOwner.PARTNER.name)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PROFILE_EDIT,
            arguments = listOf(navArgument("owner") { type = NavType.StringType }),
        ) { entry ->
            val owner = entry.arguments?.getString("owner")
                ?.let { name -> ProfileOwner.entries.firstOrNull { it.name == name } }
                ?: ProfileOwner.ME
            ProfileEditScreen(
                owner = owner,
                contentPadding = contentPadding,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                contentPadding = contentPadding,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
