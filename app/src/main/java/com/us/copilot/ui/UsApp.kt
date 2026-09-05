package com.us.copilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.us.copilot.ui.navigation.Routes
import com.us.copilot.ui.navigation.TopLevelDestination
import com.us.copilot.ui.navigation.UsNavHost

@Composable
fun UsApp(
    state: MainUiState,
    onUnlockRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showLockScreen) {
        LockScreen(onUnlock = onUnlockRequested, modifier = modifier)
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevel = remember { TopLevelDestination.entries }

    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        topLevel.any { it.route == destination.route }
    } == true

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        topLevel.forEach { destination ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTopLevel(destination.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(destination.labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            UsNavHost(
                navController = navController,
                startDestination = if (state.onboardingComplete) Routes.HOME else Routes.ONBOARDING,
                contentPadding = padding,
            )
        }
    }
}

/**
 * Tapping a bottom-nav item.
 *
 * Anchors the back stack to HOME rather than `graph.findStartDestination()`. The graph's start
 * destination is ONBOARDING on a first run, and once onboarding finishes it is popped off the
 * stack entirely — so popping up to it matched nothing and left "Today" looking dead on tap.
 * HOME is the real hub of the app and is always present, so it is the correct anchor.
 */
private fun androidx.navigation.NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
