package com.us.copilot.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.timeline.MemoryRow
import com.us.copilot.ui.theme.UsDimens
import java.time.LocalTime

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenCoach: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: (ProfileOwner) -> Unit,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
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
                item { GreetingCard(state, onOpenCheckIn) }

                item {
                    QuickActions(
                        onOpenCoach = onOpenCoach,
                        onOpenJournal = onOpenJournal,
                        onOpenProfiles = { onOpenProfile(ProfileOwner.PARTNER) },
                        onOpenTimeline = onOpenTimeline,
                    )
                }

                if (state.unresolved.isNotEmpty()) {
                    item { UnresolvedCard(count = state.unresolved.size, onClick = onOpenTimeline) }
                }

                if (state.repairStarters.isNotEmpty()) {
                    item {
                        RepairStarterCard(
                            starters = state.repairStarters,
                            onShuffle = { viewModel.shuffleRepairStarters(state.partner) },
                        )
                    }
                }

                if (state.recent.isEmpty()) {
                    item {
                        UsCard {
                            EmptyStateInline(onOpenJournal)
                        }
                    }
                } else {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.home_recent),
                            modifier = Modifier.padding(top = UsDimens.gutter),
                        )
                    }
                    items(state.recent, key = { it.id }) { memory ->
                        MemoryRow(memory = memory, onClick = onOpenTimeline)
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingCard(state: HomeUiState, onOpenCheckIn: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.home_greeting_morning
            hour < 18 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    UsCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        onClick = onOpenCheckIn,
    ) {
        Text(
            text = state.me?.name?.takeIf { it.isNotBlank() }?.let { "$greeting, $it" } ?: greeting,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.hasCheckedInToday) {
                stringResource(R.string.home_check_in_done, state.streakDays)
            } else {
                stringResource(R.string.home_check_in_prompt)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun QuickActions(
    onOpenCoach: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTimeline: () -> Unit,
) {
    UsCard {
        SectionHeader(title = stringResource(R.string.home_quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction(Icons.Filled.AutoAwesome, stringResource(R.string.home_action_check_message), Modifier.weight(1f), onOpenCoach)
            QuickAction(Icons.Filled.EditNote, stringResource(R.string.home_action_journal), Modifier.weight(1f), onOpenJournal)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction(Icons.Filled.Groups, stringResource(R.string.home_action_profiles), Modifier.weight(1f), onOpenProfiles)
            QuickAction(Icons.Filled.Favorite, stringResource(R.string.nav_timeline), Modifier.weight(1f), onOpenTimeline)
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun UnresolvedCard(count: Int, onClick: () -> Unit) {
    UsCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MoodBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(R.string.home_unresolved),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    stringResource(R.string.home_unresolved_body, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RepairStarterCard(
    starters: List<com.us.copilot.domain.usecase.RepairStarter>,
    onShuffle: () -> Unit,
) {
    UsCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                title = stringResource(R.string.repair_title),
                subtitle = stringResource(R.string.repair_body),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShuffle) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry))
            }
        }
        starters.take(3).forEach { starter ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(starter.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${starter.category} · ${starter.when_}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateInline(onOpenJournal: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onOpenJournal, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.home_action_journal))
        }
    }
}
