package com.us.copilot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.us.copilot.domain.repository.ThemeMode

/**
 * Theme entry point.
 *
 * Dynamic colour defaults to OFF in settings for this app (unlike most M3 apps) because the
 * brand palette is lifted straight from the launcher icon — letting the wallpaper repaint it
 * severs that link. Users who prefer Material You can still switch it on.
 */
@Composable
fun UsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> UsDarkColors
        else -> UsLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UsTypography,
        shapes = UsShapes,
        content = content,
    )
}
