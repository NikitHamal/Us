package com.us.copilot.ui.coach

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.us.copilot.R

/**
 * Asymmetric bubble corners — the corner nearest the speaker is tucked in (6dp) while the rest
 * stay at 22dp. It is a small thing that does most of the work in making a chat feel like a chat
 * rather than a list of rounded rectangles.
 */
private val userShape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
private val coachShape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)

private const val MAX_BUBBLE_FRACTION = 0.86f

@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = userShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = bubbleMaxWidth()),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        CoachAvatar()
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = coachShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = bubbleMaxWidth()),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            )
        }
    }
}

/** Small brand mark that anchors every coach turn to the icon's heart motif. */
@Composable
fun CoachAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Three dots breathing out of phase — the standard "typing" affordance. */
@Composable
fun ThinkingBubble(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        CoachAvatar()
        Spacer(Modifier.width(10.dp))
        Surface(shape = coachShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index -> PulsingDot(delayMillis = index * 160) }
            }
        }
    }
}

@Composable
private fun PulsingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )
    Box(
        Modifier
            .size(7.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
    )
}

/** Wraps analysis cards so they sit in the coach's column, inset to match bubbles. */
@Composable
fun CoachAttachment(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth()) {
        Spacer(Modifier.width(40.dp))
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun bubbleMaxWidth() =
    with(LocalConfiguration.current) {
        (screenWidthDp * MAX_BUBBLE_FRACTION).dp
    }

/**
 * Inline failure, styled as a coach turn rather than a full-screen error state — a failed
 * analysis is one bad turn in a conversation, not a dead end for the whole screen.
 */
@Composable
fun ErrorBubbleCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = coachShape,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.padding(top = 4.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = stringResource(R.string.action_retry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
