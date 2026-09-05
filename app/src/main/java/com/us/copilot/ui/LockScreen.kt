package com.us.copilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.ui.theme.UsDimens

/**
 * Full-screen lock.
 *
 * Prompts once automatically, then hands control back to the user. It deliberately does not
 * re-prompt in a loop: if someone cancels, hammering the system dialog at them is hostile, and
 * on devices with no enrolled credential it would be an infinite loop.
 */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    isAuthenticating: Boolean = false,
    message: LockMessage? = null,
) {
    LaunchedEffect(Unit) { onUnlock() }

    val transition = rememberInfiniteTransition(label = "lock")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(UsDimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(pulse)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = UsDimens.sectionSpacing),
            )
            Text(
                stringResource(R.string.lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = stringResource(
                        when (message) {
                            LockMessage.CANCELLED -> R.string.lock_cancelled
                            LockMessage.FAILED -> R.string.lock_failed
                            LockMessage.NOT_ENROLLED -> R.string.lock_not_enrolled
                            LockMessage.NO_HARDWARE -> R.string.lock_no_hardware
                            null -> R.string.lock_body
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (isAuthenticating) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = UsDimens.sectionSpacing)
                        .size(28.dp),
                )
            } else {
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.padding(top = UsDimens.sectionSpacing),
                ) { Text(stringResource(R.string.lock_unlock)) }
            }
        }
    }
}
