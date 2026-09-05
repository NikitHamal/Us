package com.us.copilot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Expressive, generously rounded. Cards use the 28dp corner called for in the design brief. */
val UsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

object UsDimens {
    val cardCorner = 28.dp
    val screenPadding = 20.dp
    val gutter = 16.dp
    val itemSpacing = 12.dp
    val sectionSpacing = 24.dp
    val minTouchTarget = 48.dp
}
