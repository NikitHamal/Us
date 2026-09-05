package com.us.copilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.us.copilot.R

/**
 * Poppins, served by the Google Fonts downloadable-font provider.
 *
 * Downloadable fonts keep ~400KB of binaries out of the APK and let the platform share one cached
 * copy across apps. If the provider is unavailable (no Play Services, offline first run, or a
 * restricted device) Compose silently falls back to the device sans-serif, so text always renders
 * — worst case it is not Poppins. That tradeoff is deliberate: this app must work fully offline,
 * so typography can degrade but must never block.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val poppins = GoogleFont("Poppins")

private fun poppinsFamily(): FontFamily = FontFamily(
    Font(googleFont = poppins, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = poppins, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = poppins, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = poppins, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = poppins, fontProvider = provider, weight = FontWeight.Bold),
    Font(
        googleFont = poppins, fontProvider = provider,
        weight = FontWeight.Normal, style = FontStyle.Italic,
    ),
)

val Poppins: FontFamily = poppinsFamily()

private val readableLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * M3 Expressive scale. Poppins is geometric and runs optically large, so display and headline
 * sizes carry tight negative tracking to stop them sprawling, while body copy gets generous
 * line height because this app is read slowly — these are reflections, not feed posts.
 */
val UsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.2).sp,
        lineHeightStyle = readableLineHeight,
    ),
    displayMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 35.sp, lineHeight = 44.sp, letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp,
        lineHeightStyle = readableLineHeight,
    ),
    bodyMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp,
        lineHeightStyle = readableLineHeight,
    ),
    bodySmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
)
