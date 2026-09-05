package com.us.copilot.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated onboarding illustrations, drawn with Compose Canvas.
 *
 * Drawn rather than shipped as assets for three reasons: they inherit the theme palette exactly
 * (so light/dark and any future palette change are free), they add no binary weight or animation
 * dependency, and they stay crisp at any density. Every animation is a slow infinite transition —
 * this is an app about calming down, so nothing should feel urgent.
 */

private const val BREATH_MS = 3200
private const val DRIFT_MS = 9000

/** Two hearts drifting toward and away from each other. Used on the welcome page. */
@Composable
fun TwoHeartsArt(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hearts")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary

    ArtFrame(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val gap = 34.dp.toPx() * (1f - breath * 0.45f)
        val scale = 0.92f + breath * 0.1f

        drawHalo(center = Offset(cx, cy), radius = size.minDimension * 0.42f, color = container)

        translate(left = -gap, top = 6.dp.toPx()) {
            drawHeart(
                center = Offset(cx, cy),
                radius = size.minDimension * 0.19f * scale,
                color = primary.copy(alpha = 0.92f),
            )
        }
        translate(left = gap, top = -6.dp.toPx()) {
            drawHeart(
                center = Offset(cx, cy),
                radius = size.minDimension * 0.19f * scale,
                color = tertiary.copy(alpha = 0.85f),
            )
        }
    }
}

/** A shield with a slow sweep, for the privacy page. */
@Composable
fun PrivacyShieldArt(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shield")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(DRIFT_MS, easing = LinearEasing),
        ),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.outlineVariant

    ArtFrame(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.3f

        drawHalo(center, r * 1.5f, container)

        rotate(degrees = sweep, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, primary.copy(alpha = 0.5f), Color.Transparent),
                    center,
                ),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - r * 1.25f, center.y - r * 1.25f),
                size = Size(r * 2.5f, r * 2.5f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        drawCircle(color = outline, radius = r * 1.25f, center = center, style = Stroke(1.dp.toPx()))

        val shield = Path().apply {
            val w = r * 1.05f * pulse
            val h = r * 1.3f * pulse
            moveTo(center.x, center.y - h)
            cubicTo(center.x + w, center.y - h * 0.75f, center.x + w, center.y - h * 0.1f, center.x + w * 0.86f, center.y + h * 0.32f)
            cubicTo(center.x + w * 0.6f, center.y + h * 0.8f, center.x + w * 0.24f, center.y + h * 0.96f, center.x, center.y + h)
            cubicTo(center.x - w * 0.24f, center.y + h * 0.96f, center.x - w * 0.6f, center.y + h * 0.8f, center.x - w * 0.86f, center.y + h * 0.32f)
            cubicTo(center.x - w, center.y - h * 0.1f, center.x - w, center.y - h * 0.75f, center.x, center.y - h)
            close()
        }
        drawPath(shield, color = primary.copy(alpha = 0.9f))
    }
}

/** Orbiting dots around a steady centre, for the ethics page. */
@Composable
fun BalanceArt(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "balance")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(DRIFT_MS, easing = LinearEasing)),
        label = "angle",
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val container = MaterialTheme.colorScheme.primaryContainer

    ArtFrame(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbit = size.minDimension * 0.3f

        drawHalo(center, orbit * 1.4f, container)
        drawCircle(color = primary.copy(alpha = 0.9f), radius = 11.dp.toPx(), center = center)

        listOf(0f, 120f, 240f).forEachIndexed { index, offsetDeg ->
            val rad = Math.toRadians((angle + offsetDeg).toDouble())
            val dotCenter = Offset(
                x = center.x + (orbit * cos(rad)).toFloat(),
                y = center.y + (orbit * sin(rad)).toFloat() * 0.55f,
            )
            drawCircle(
                color = if (index % 2 == 0) tertiary else primary,
                radius = 7.dp.toPx(),
                center = dotCenter,
                alpha = 0.85f,
            )
        }
    }
}

@Composable
private fun ArtFrame(modifier: Modifier, onDraw: DrawScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxWidth().height(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(190.dp), onDraw = onDraw)
    }
}

private fun DrawScope.drawHalo(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** Heart built from two arcs and a V, sized off [radius]. */
private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.95f)
        cubicTo(
            center.x - radius * 1.6f, center.y - radius * 0.25f,
            center.x - radius * 0.6f, center.y - radius * 1.5f,
            center.x, center.y - radius * 0.5f,
        )
        cubicTo(
            center.x + radius * 0.6f, center.y - radius * 1.5f,
            center.x + radius * 1.6f, center.y - radius * 0.25f,
            center.x, center.y + radius * 0.95f,
        )
        close()
    }
    drawPath(path, color = color)
}
