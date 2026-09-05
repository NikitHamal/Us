package com.us.copilot.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.ui.components.EmptyState
import com.us.copilot.ui.components.ErrorState
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes
import com.us.copilot.ui.util.messageFor

@Composable
fun CoachScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    prefillText: String? = null,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedLabel = stringResource(R.string.share_saved)

    LaunchedEffect(prefillText) {
        if (!prefillText.isNullOrBlank()) viewModel.prefill(prefillText)
    }

    LaunchedEffect(state.savedMessage) {
        if (state.savedMessage) {
            snackbarHostState.showSnackbar(savedLabel)
            viewModel.consumeSavedMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.coach_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = UsDimens.screenPadding,
                end = UsDimens.screenPadding,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + UsDimens.sectionSpacing,
            ),
            verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
        ) {
            item {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text(stringResource(R.string.coach_input_hint)) },
                    minLines = 4,
                    maxLines = 10,
                    shape = UsShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = viewModel::analyze,
                        enabled = state.hasInput && !state.isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null)
                        Text(
                            stringResource(R.string.coach_analyze),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::rephrase,
                        enabled = state.hasInput && !state.isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text(
                            stringResource(R.string.coach_rephrase),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = state.isBusy,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    UsCard {
                        Text(
                            stringResource(R.string.coach_analyzing),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        LoadingState(Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                }
            }

            state.error?.let { error ->
                item {
                    UsCard {
                        ErrorState(
                            icon = Icons.Filled.AutoAwesome,
                            title = stringResource(R.string.error_generic_title),
                            message = messageFor(error),
                            retryLabel = stringResource(R.string.action_retry),
                            onRetry = viewModel::analyze,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }

            state.tone?.let { tone -> item { ToneResultCard(tone) } }

            state.rephrase?.let { rephrase ->
                item { RephraseCard(rephrase = rephrase, onUseRewrite = viewModel::useRewrite) }
            }

            if (state.hasResult) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.saveAsMemory(Speaker.ME, MemorySource.MANUAL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.coach_save_memory)) }
                }
            }

            if (!state.hasInput && !state.hasResult && state.error == null) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = stringResource(R.string.coach_empty_title),
                        message = stringResource(R.string.coach_empty_body),
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }
        }
    }
}
