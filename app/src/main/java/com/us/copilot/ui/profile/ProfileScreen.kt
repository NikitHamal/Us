package com.us.copilot.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.core.model.Profile
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.ui.components.EmptyState
import com.us.copilot.ui.components.LabeledProgress
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

@Composable
fun ProfileScreen(
    owner: ProfileOwner,
    contentPadding: PaddingValues,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(owner) { viewModel.load(owner) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (owner == ProfileOwner.ME) R.string.profile_me else R.string.profile_partner,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onEdit,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.action_edit)) },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))

            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.Person,
                title = stringResource(R.string.profile_empty_title),
                message = stringResource(R.string.profile_empty_body),
                actionLabel = stringResource(R.string.action_edit),
                onAction = onEdit,
                modifier = Modifier.padding(innerPadding),
            )

            else -> {
                val profile = state.profile ?: return@Scaffold
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
                    item { HeaderCard(profile) }
                    item { CoreTraitsCard(profile) }
                    item { ListsCard(profile) }
                    item { BigFiveCard(profile) }

                    if (state.history.size > 1) {
                        item {
                            UsCard {
                                SectionHeader(title = stringResource(R.string.profile_history))
                                state.history.filterNot { it.isActive }.take(5).forEach { version ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(
                                                stringResource(R.string.profile_version, version.version),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                TimeUtils.relative(version.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = { viewModel.restore(version.id) }) {
                                            Text(stringResource(R.string.profile_restore))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(profile: Profile) {
    UsCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            profile.name.ifBlank { "Unnamed" },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            stringResource(R.string.profile_version, profile.version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(14.dp))
        LabeledProgress(
            label = stringResource(R.string.profile_completeness),
            progress = profile.completeness,
            trailing = "${(profile.completeness * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CoreTraitsCard(profile: Profile) {
    UsCard {
        SectionHeader(title = stringResource(R.string.profile_attachment))
        Text(profile.attachmentStyle.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            profile.attachmentStyle.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SectionHeader(title = stringResource(R.string.profile_conflict))
        Text(profile.conflictStyle.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            profile.conflictStyle.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profile.loveLanguages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(title = stringResource(R.string.profile_love))
            profile.loveLanguages.forEachIndexed { index, language ->
                Text(
                    "${index + 1}. ${language.label}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ListsCard(profile: Profile) {
    UsCard {
        ChipSection(stringResource(R.string.profile_triggers), profile.triggers)
        ChipSection(stringResource(R.string.profile_soothers), profile.soothers)
        ChipSection(stringResource(R.string.profile_stress), profile.stressPatterns)
        ChipSection(stringResource(R.string.profile_comm), profile.commPreferences)
    }
}

@Composable
private fun ChipSection(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(Modifier.padding(bottom = UsDimens.gutter)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            values.forEach { value ->
                AssistChip(onClick = {}, label = { Text(value) }, shape = UsShapes.small)
            }
        }
    }
}

@Composable
private fun BigFiveCard(profile: Profile) {
    UsCard {
        SectionHeader(title = stringResource(R.string.profile_bigfive))
        profile.bigFive.asPairs.forEach { (label, score) ->
            LabeledProgress(
                label = label,
                progress = score / 100f,
                trailing = "$score",
                modifier = Modifier.padding(vertical = 5.dp),
            )
        }
    }
}
