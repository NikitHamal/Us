package com.us.copilot.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.ui.components.EmptyState
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

/**
 * Captured notification history.
 *
 * The screen is built around one idea: capture and AI access are separate decisions. Every row
 * shows what was captured; the share control on each row is the only thing that hands text to the
 * model, and the header keeps the current shared count permanently visible so that state is never
 * a surprise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))

            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.NotificationsOff,
                title = stringResource(R.string.notif_history_empty_title),
                message = if (!state.captureEnabled) {
                    stringResource(R.string.notif_history_empty_disabled)
                } else if (state.watchedCount == 0) {
                    stringResource(R.string.notif_history_empty_no_apps)
                } else {
                    stringResource(R.string.notif_history_empty_body)
                },
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
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
                    HistoryHeader(
                        total = state.items.size,
                        shared = state.sharedCount,
                        filter = state.filter,
                        onFilter = viewModel::setFilter,
                        onStopSharingAll = viewModel::stopSharingAll,
                        onClearAll = { confirmClear = true },
                    )
                }

                items(
                    count = state.visible.size,
                    key = { index -> state.visible[index].id },
                ) { index ->
                    val item = state.visible[index]
                    CapturedNotificationRow(
                        item = item,
                        onToggleShared = { viewModel.toggleShared(item) },
                        onDelete = { viewModel.delete(item) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.notif_clear_all_title)) },
            text = { Text(stringResource(R.string.notif_clear_all_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        confirmClear = false
                    },
                ) { Text(stringResource(R.string.notif_clear_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = UsShapes.large,
        )
    }
}

@Composable
private fun HistoryHeader(
    total: Int,
    shared: Int,
    filter: HistoryFilter,
    onFilter: (HistoryFilter) -> Unit,
    onStopSharingAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.notif_history_summary, total, shared),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = filter == HistoryFilter.ALL,
                onClick = { onFilter(HistoryFilter.ALL) },
                label = { Text(stringResource(R.string.notif_filter_all)) },
                shape = UsShapes.small,
            )
            FilterChip(
                selected = filter == HistoryFilter.SHARED,
                onClick = { onFilter(HistoryFilter.SHARED) },
                label = { Text(stringResource(R.string.notif_filter_shared)) },
                shape = UsShapes.small,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (shared > 0) {
                TextButton(onClick = onStopSharingAll) {
                    Text(stringResource(R.string.notif_stop_sharing_all))
                }
            }
            if (total > 0) {
                TextButton(onClick = onClearAll) {
                    Text(
                        stringResource(R.string.notif_clear_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
