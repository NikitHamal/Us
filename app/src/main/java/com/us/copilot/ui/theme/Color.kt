package com.us.copilot.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette derived directly from the app icon so brand and product read as one thing.
 *
 * The launcher icon is a deep rose field (#8F4A5C) carrying two interlocking hearts in white and
 * blush (#FFD9E0). Those three values are the spine of the whole system:
 *
 *  - [Rose] 0xFF8F4A5C — the icon background, our primary.
 *  - [Blush] 0xFFFFD9E0 — the inner heart, our primary container / soft surfaces.
 *  - [Ink] 0xFF2A1015 — near-black with rose bias, so text never looks neutral-grey next to blush.
 *
 * Warm neutrals throughout: surfaces are tinted a few points toward rose rather than pure white,
 * which is what keeps a minimalist layout from feeling clinical.
 */

// --- Brand constants, straight from the icon ---
val Rose = Color(0xFF8F4A5C)
val Blush = Color(0xFFFFD9E0)
val Ink = Color(0xFF2A1015)

// Light
private val RoseOnLight = Color(0xFFFFFFFF)
private val RoseContainerLight = Blush
private val OnRoseContainerLight = Color(0xFF3A0719)

private val PlumLight = Color(0xFF7A5560)
private val PlumContainerLight = Color(0xFFFFE2E8)
private val OnPlumContainerLight = Color(0xFF2B151A)

// Sage stays as the calm/tertiary accent — the one cool note that lets positive
// states read as distinct from the warm brand without leaving the palette.
private val SageLight = Color(0xFF4C6444)
private val SageContainerLight = Color(0xFFCEEBC1)
private val OnSageContainerLight = Color(0xFF0A2006)

// Dark
private val RoseDark = Color(0xFFFFB1C2)
private val OnRoseDark = Color(0xFF561D2E)
private val RoseContainerDark = Color(0xFF733345)
private val OnRoseContainerDark = Blush

private val PlumDark = Color(0xFFE4BDC4)
private val PlumContainerDark = Color(0xFF5B3F45)
private val SageDark = Color(0xFFB2CFA6)
private val SageContainerDark = Color(0xFF354C2E)

val UsLightColors = lightColorScheme(
    primary = Rose,
    onPrimary = RoseOnLight,
    primaryContainer = RoseContainerLight,
    onPrimaryContainer = OnRoseContainerLight,
    secondary = PlumLight,
    onSecondary = Color.White,
    secondaryContainer = PlumContainerLight,
    onSecondaryContainer = OnPlumContainerLight,
    tertiary = SageLight,
    onTertiary = Color.White,
    tertiaryContainer = SageContainerLight,
    onTertiaryContainer = OnSageContainerLight,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F8),
    onBackground = Ink,
    surface = Color(0xFFFFF8F8),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF6E2E6),
    onSurfaceVariant = Color(0xFF574449),
    outline = Color(0xFF8A757A),
    outlineVariant = Color(0xFFDDC8CC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1F3),
    surfaceContainer = Color(0xFFFDEBEE),
    surfaceContainerHigh = Color(0xFFF9E5E9),
    surfaceContainerHighest = Color(0xFFF3E0E3),
    inverseSurface = Color(0xFF3A2A2E),
    inverseOnSurface = Color(0xFFFFEDEF),
    inversePrimary = RoseDark,
    scrim = Color(0xFF000000),
)

val UsDarkColors = darkColorScheme(
    primary = RoseDark,
    onPrimary = OnRoseDark,
    primaryContainer = RoseContainerDark,
    onPrimaryContainer = OnRoseContainerDark,
    secondary = PlumDark,
    onSecondary = Color(0xFF43292F),
    secondaryContainer = PlumContainerDark,
    onSecondaryContainer = Blush,
    tertiary = SageDark,
    onTertiary = Color(0xFF203618),
    tertiaryContainer = SageContainerDark,
    onTertiaryContainer = Color(0xFFCEEBC1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17100F),
    onBackground = Color(0xFFF1DFE1),
    surface = Color(0xFF17100F),
    onSurface = Color(0xFFF1DFE1),
    surfaceVariant = Color(0xFF574449),
    onSurfaceVariant = Color(0xFFDDC8CC),
    outline = Color(0xFFA68F94),
    outlineVariant = Color(0xFF574449),
    surfaceContainerLowest = Color(0xFF120B0C),
    surfaceContainerLow = Color(0xFF201719),
    surfaceContainer = Color(0xFF251B1D),
    surfaceContainerHigh = Color(0xFF302527),
    surfaceContainerHighest = Color(0xFF3C2F32),
    inverseSurface = Color(0xFFF1DFE1),
    inverseOnSurface = Color(0xFF3A2A2E),
    inversePrimary = Rose,
    scrim = Color(0xFF000000),
)

/** Semantic colours used by charts and risk badges, resolved per theme. */
object UsSemantic {
    val positive = Color(0xFF3E7A4E)
    val positiveDark = Color(0xFF9DD5A9)
    val caution = Color(0xFFB07000)
    val cautionDark = Color(0xFFF6C46A)
    val danger = Color(0xFFB3261E)
    val dangerDark = Color(0xFFFFB4AB)
}

/**
 * Brand gradient stops for hero surfaces (home header, chat empty state, onboarding).
 * Kept as an explicit list so gradients stay identical everywhere instead of being
 * re-invented per screen.
 */
object UsGradients {
    val warmLight = listOf(Color(0xFFFFE3E9), Color(0xFFFFF1F0), Color(0xFFF6ECFF))
    val warmDark = listOf(Color(0xFF3A2028), Color(0xFF261A1D), Color(0xFF221A2B))
}
