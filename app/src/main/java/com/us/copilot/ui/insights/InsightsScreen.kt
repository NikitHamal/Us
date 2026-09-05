package com.us.copilot.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.us.copilot.ui.components.MetricTile
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsDimens

@Composable
fun InsightsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Filled.Autorenew,
                            contentDescription = stringResource(R.string.insights_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))

            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.Insights,
                title = stringResource(R.string.insights_empty_title),
                message = stringResource(R.string.insights_empty_body),
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
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
                    ) {
                        MetricTile(
                            label = stringResource(R.string.insights_total),
                            value = state.summary.totalMemories.toString(),
                            icon = Icons.AutoMirrored.Filled.Notes,
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = stringResource(R.string.insights_unresolved),
                            value = state.summary.unresolvedCount.toString(),
                            icon = Icons.Filled.PendingActions,
                            accent = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
                    ) {
                        MetricTile(
                            label = stringResource(R.string.insights_conflicts),
                            value = state.summary.conflictsLast30Days.toString(),
                            icon = Icons.Filled.LocalFireDepartment,
                            accent = MaterialTheme.colorScheme.secondary,
                            caption = conflictCaption(state.summary.conflictDelta),
                            modifier = Modifier.weight(1f),
                        )
                        MetricTile(
                            label = stringResource(R.string.insights_repairs),
                            value = state.summary.repairAttempts.toString(),
                            icon = Icons.Filled.Healing,
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    UsCard {
                        MetricTile(
                            label = stringResource(R.string.insights_ratio),
                            value = String.format("%.1f : 1", state.summary.positiveToNegativeRatio),
                            icon = Icons.Filled.Balance,
                            accent = if (state.summary.meetsMagicRatio) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(
                                if (state.summary.meetsMagicRatio) {
                                    R.string.insights_ratio_good
                                } else {
                                    R.string.insights_ratio_low
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (state.cadence.hasSignal) {
                    item {
                        UsCard {
                            Text(
                                stringResource(R.string.insights_cadence),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.insights_cadence_body,
                                    String.format("%.0f", state.cadence.averageDaysBetween),
                                    state.cadence.busiestWeekday ?: "weekday",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                if (state.summary.connectionTrend.size >= 2) {
                    item { ConnectionTrendCard(state.summary.connectionTrend) }
                }

                if (state.summary.topTriggers.isNotEmpty()) {
                    item { TriggersCard(state.summary.topTriggers) }
                }

                item { HorsemenCard(state.summary.horsemen) }

                state.report?.let { report ->
                    if (!report.isEmpty) {
                        item {
                            PatternReportCard(
                                observations = report.observations,
                                suggestions = report.suggestions,
                            )
                        }
                    }
                }

                if (state.isAnalyzing) {
                    item { LoadingState(Modifier.fillMaxWidth().padding(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun conflictCaption(delta: Int): String = when {
    delta < 0 -> stringResource(R.string.insights_conflict_down, -delta)
    delta > 0 -> stringResource(R.string.insights_conflict_up, delta)
    else -> stringResource(R.string.insights_conflict_flat)
}
