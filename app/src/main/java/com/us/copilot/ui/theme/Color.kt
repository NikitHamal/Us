package com.us.copilot.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for devices without dynamic color (< Android 12) or when the user turns it off.
 * Warm rose primary, deep plum secondary, sage tertiary — intimate without being saccharine.
 */

private val RoseLight = Color(0xFF8F4A5C)
private val RoseOnLight = Color(0xFFFFFFFF)
private val RoseContainerLight = Color(0xFFFFD9E0)
private val OnRoseContainerLight = Color(0xFF3A0719)

private val PlumLight = Color(0xFF75565C)
private val PlumContainerLight = Color(0xFFFFD9E0)
private val OnPlumContainerLight = Color(0xFF2B151A)

private val SageLight = Color(0xFF4C6444)
private val SageContainerLight = Color(0xFFCEEBC1)
private val OnSageContainerLight = Color(0xFF0A2006)

private val RoseDark = Color(0xFFFFB1C2)
private val OnRoseDark = Color(0xFF561D2E)
private val RoseContainerDark = Color(0xFF733345)
private val OnRoseContainerDark = Color(0xFFFFD9E0)

private val PlumDark = Color(0xFFE4BDC4)
private val PlumContainerDark = Color(0xFF5B3F45)
private val SageDark = Color(0xFFB2CFA6)
private val SageContainerDark = Color(0xFF354C2E)

val UsLightColors = lightColorScheme(
    primary = RoseLight,
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
    onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF524346),
    outline = Color(0xFF847376),
    outlineVariant = Color(0xFFD6C2C5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFCEAEC),
    surfaceContainerHigh = Color(0xFFF7E4E7),
    surfaceContainerHighest = Color(0xFFF1DFE1),
    inverseSurface = Color(0xFF382E30),
    inverseOnSurface = Color(0xFFFFEDEF),
    inversePrimary = RoseDark,
)

val UsDarkColors = darkColorScheme(
    primary = RoseDark,
    onPrimary = OnRoseDark,
    primaryContainer = RoseContainerDark,
    onPrimaryContainer = OnRoseContainerDark,
    secondary = PlumDark,
    onSecondary = Color(0xFF43292F),
    secondaryContainer = PlumContainerDark,
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = SageDark,
    onTertiary = Color(0xFF203618),
    tertiaryContainer = SageContainerDark,
    onTertiaryContainer = Color(0xFFCEEBC1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF1DFE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF1DFE1),
    surfaceVariant = Color(0xFF524346),
    onSurfaceVariant = Color(0xFFD6C2C5),
    outline = Color(0xFF9F8C90),
    outlineVariant = Color(0xFF524346),
    surfaceContainerLowest = Color(0xFF130C0E),
    surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF261D1F),
    surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3D3234),
    inverseSurface = Color(0xFFF1DFE1),
    inverseOnSurface = Color(0xFF382E30),
    inversePrimary = RoseLight,
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
