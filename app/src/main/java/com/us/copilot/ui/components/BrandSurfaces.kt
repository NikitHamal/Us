package com.us.copilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.us.copilot.ui.theme.UsGradients
import com.us.copilot.ui.theme.UsShapes

/**
 * Hero surface with the brand's warm diagonal wash.
 *
 * Used sparingly — the home header and the onboarding welcome only. A gradient loses all meaning
 * the moment it is everywhere, so the rest of the app stays on flat tonal surfaces and lets this
 * one carry the brand.
 */
@Composable
fun BrandHero(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = UsShapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stops = if (isSystemInDarkTheme()) UsGradients.warmDark else UsGradients.warmLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = stops,
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        Column(Modifier.padding(24.dp), content = content)
    }
}

/**
 * Low-emphasis container for grouped rows (settings, profile fields). Flatter than [UsCard]
 * so lists of them do not read as a stack of competing cards.
 */
@Composable
fun QuietGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = UsShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** Full-bleed subtle background wash for hero screens. */
@Composable
fun BrandBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val stops = if (isSystemInDarkTheme()) UsGradients.warmDark else UsGradients.warmLight
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(stops.first(), MaterialTheme.colorScheme.background),
            ),
        ),
        content = content,
    )
}
