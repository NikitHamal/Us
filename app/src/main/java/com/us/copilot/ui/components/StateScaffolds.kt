package com.us.copilot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.us.copilot.ui.theme.UsDimens

/** Centered spinner with an accessible label. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    label: String = "Loading",
) {
    Box(
        modifier = modifier.fillMaxSize().semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

/** Friendly empty state with an optional primary action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(UsDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UsDimens.gutter),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = UsDimens.sectionSpacing),
            ) { Text(actionLabel) }
        }
    }
}

/** Error state that always offers a way forward. */
@Composable
fun ErrorState(
    icon: ImageVector,
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(UsDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UsDimens.gutter),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = UsDimens.sectionSpacing),
        ) { Text(retryLabel) }
    }
}

/** Crossfades between loading, error, empty and content without layout jumps. */
@Composable
fun <T> StateSwitcher(
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    value: T,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { LoadingState() },
    errorContent: @Composable (String) -> Unit,
    empty: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) { loading() }
        AnimatedVisibility(visible = !isLoading && error != null, enter = fadeIn(), exit = fadeOut()) {
            error?.let { errorContent(it) }
        }
        AnimatedVisibility(
            visible = !isLoading && error == null && isEmpty,
            enter = fadeIn(), exit = fadeOut(),
        ) { empty() }
        AnimatedVisibility(
            visible = !isLoading && error == null && !isEmpty,
            enter = fadeIn(), exit = fadeOut(),
        ) { content(value) }
    }
}
