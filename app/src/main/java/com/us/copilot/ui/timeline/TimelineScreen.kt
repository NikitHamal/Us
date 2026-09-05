package com.us.copilot.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun TimelineScreen(
    contentPadding: PaddingValues,
    onAddMoment: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showFilterSheet) {
        TimelineFilterSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { viewModel.setFilterSheetVisible(false) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                actions = {
                    IconButton(onClick = { viewModel.setFilterSheetVisible(true) }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.timeline_filter),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMoment,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_action_journal)) },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = UsDimens.screenPadding,
                end = UsDimens.screenPadding,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
        ) {
            item {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.timeline_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = UsShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.isLoading -> item { LoadingState(Modifier.fillMaxWidth().padding(48.dp)) }

                state.isEmpty && state.isFiltered -> item {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.timeline_no_results_title),
                        message = stringResource(R.string.timeline_no_results_body),
                        actionLabel = stringResource(R.string.timeline_filter_clear),
                        onAction = viewModel::clearFilters,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }

                state.isEmpty -> item {
                    EmptyState(
                        icon = Icons.Outlined.Book,
                        title = stringResource(R.string.timeline_empty_title),
                        message = stringResource(R.string.timeline_empty_body),
                        actionLabel = stringResource(R.string.home_action_journal),
                        onAction = onAddMoment,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }

                else -> items(state.memories, key = { it.id }) { memory ->
                    MemoryRow(
                        memory = memory,
                        onClick = { viewModel.setResolved(memory.id, !memory.isUnresolved) },
                        onToggleResolved = { resolved -> viewModel.setResolved(memory.id, resolved) },
                    )
                }
            }
        }
    }
}
