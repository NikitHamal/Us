package com.us.copilot.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.BuildConfig
import com.us.copilot.R
import com.us.copilot.ai.nebians.NebiansCatalog
import com.us.copilot.domain.repository.ThemeMode
import com.us.copilot.ui.coach.NebiansModelList
import com.us.copilot.ui.coach.NebiansProviderList
import com.us.copilot.ui.coach.ReasoningControls
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes
import com.us.copilot.ui.util.messageFor

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenWatchedApps: () -> Unit,
    onOpenNotificationHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val savedLabel = stringResource(R.string.settings_credentials_saved)
    val wipedLabel = stringResource(R.string.settings_wiped)

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    if (state.showWipeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setWipeDialogVisible(false) },
            title = { Text(stringResource(R.string.settings_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wipe_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.wipeEverything(wipedLabel) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setWipeDialogVisible(false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UsDimens.screenPadding)
                .padding(bottom = contentPadding.calculateBottomPadding() + UsDimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
        ) {
            UsCard {
                SectionHeader(title = stringResource(R.string.settings_section_privacy))
                SettingsToggle(
                    title = stringResource(R.string.settings_biometric),
                    body = stringResource(R.string.settings_biometric_body),
                    checked = state.prefs.biometricLockEnabled,
                    onCheckedChange = viewModel::setBiometric,
                )
                SettingsToggle(
                    title = stringResource(R.string.settings_notification),
                    body = stringResource(R.string.settings_notification_body),
                    checked = state.prefs.notificationCaptureEnabled,
                    onCheckedChange = viewModel::setNotificationCapture,
                )
                if (state.prefs.notificationCaptureEnabled) {
                    SettingsToggle(
                        title = stringResource(R.string.settings_tone_check),
                        body = stringResource(R.string.settings_tone_check_body),
                        checked = state.prefs.notificationToneCheckEnabled,
                        onCheckedChange = viewModel::setNotificationToneCheck,
                    )
                    SettingsLink(
                        title = stringResource(R.string.settings_watched_apps),
                        body = if (state.prefs.watchedPackages.isEmpty()) {
                            stringResource(R.string.notif_apps_none)
                        } else {
                            stringResource(
                                R.string.notif_apps_selected,
                                state.prefs.watchedPackages.size,
                            )
                        },
                        onClick = onOpenWatchedApps,
                    )
                    SettingsLink(
                        title = stringResource(R.string.settings_notification_history),
                        body = stringResource(R.string.settings_notification_history_body),
                        onClick = onOpenNotificationHistory,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(stringResource(R.string.settings_notification_grant)) }
                }
            }

            AiProviderCard(state = state, viewModel = viewModel, savedLabel = savedLabel)

            NebiansProviderCard(state = state, viewModel = viewModel, savedLabel = savedLabel)

            UsCard {
                SectionHeader(title = stringResource(R.string.settings_section_appearance))
                SettingsToggle(
                    title = stringResource(R.string.settings_dynamic_color),
                    body = stringResource(R.string.settings_dynamic_color_body),
                    checked = state.prefs.dynamicColorEnabled,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                            label = {
                                Text(
                                    mode.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.settings_section_data))
                Text(
                    stringResource(R.string.settings_wipe_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { viewModel.setWipeDialogVisible(true) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text(stringResource(R.string.settings_wipe)) }
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.settings_section_about))
                Text(
                    stringResource(
                        R.string.settings_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.settings_ethics),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AiProviderCard(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    savedLabel: String,
) {
    UsCard {
        SectionHeader(title = stringResource(R.string.settings_section_ai))

        Text(
            stringResource(R.string.settings_offline_model),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            if (state.modelState.isBlank()) {
                stringResource(R.string.settings_offline_model_rules)
            } else {
                stringResource(R.string.settings_offline_model_ready, state.modelState)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        SettingsToggle(
            title = stringResource(R.string.settings_cloud_toggle),
            body = stringResource(R.string.settings_cloud_body),
            checked = state.prefs.cloudAiEnabled,
            onCheckedChange = viewModel::setCloudAi,
        )

        if (state.prefs.cloudAiEnabled) {
            OutlinedTextField(
                value = state.credentials.baseUrl,
                onValueChange = { value -> viewModel.editCredentials { it.copy(baseUrl = value) } },
                label = { Text(stringResource(R.string.settings_base_url)) },
                placeholder = { Text(stringResource(R.string.settings_base_url_hint)) },
                singleLine = true,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = state.credentials.apiKey,
                onValueChange = { value -> viewModel.editCredentials { it.copy(apiKey = value) } },
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = state.credentials.modelName,
                onValueChange = { value -> viewModel.editCredentials { it.copy(modelName = value) } },
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text(stringResource(R.string.settings_model_hint)) },
                singleLine = true,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = state.credentials.embeddingModel,
                onValueChange = { value ->
                    viewModel.editCredentials { it.copy(embeddingModel = value) }
                },
                label = { Text(stringResource(R.string.settings_embedding_model)) },
                singleLine = true,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.saveCredentials(savedLabel) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = state.connectionTest !is ConnectionTest.Running,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.settings_test_connection)) }
            }

            when (val test = state.connectionTest) {
                ConnectionTest.Success -> Text(
                    stringResource(R.string.settings_connection_ok),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                is ConnectionTest.Failed -> Text(
                    stringResource(R.string.settings_connection_failed, messageFor(test.error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> Unit
            }
        }
    }
}

/**
 * The Nebians fleet: provider + live model list, reasoning config for
 * supported models, key fields only for providers that need one, and file
 * support notes where uploads apply.
 */
@Composable
private fun NebiansProviderCard(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    savedLabel: String,
) {
    val selected = NebiansCatalog.find(state.nebians.providerSlug)
    UsCard {
        SectionHeader(title = stringResource(R.string.settings_section_nebians))
        Text(
            stringResource(R.string.settings_nebians_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        NebiansProviderList(
            selectedSlug = state.nebians.providerSlug,
            onSelect = viewModel::selectNebiansProvider,
        )

        if (selected != null && selected.models.isNotEmpty()) {
            Text(
                stringResource(R.string.coach_model_model),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            NebiansModelList(
                provider = selected,
                selectedModelId = state.nebians.modelId,
                onSelect = viewModel::selectNebiansModel,
            )
        }

        if (selected != null) {
            ReasoningControls(
                config = state.nebians,
                provider = selected,
                onSelectEffort = viewModel::selectNebiansEffort,
                onSelectTemperature = viewModel::selectNebiansTemperature,
                onSelectMaxTokens = viewModel::selectNebiansMaxTokens,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (selected.supportsFiles) {
                Text(
                    stringResource(R.string.settings_nebians_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (selected != null && (selected.keyRequired || selected.slug == "custom")) {
            if (selected.slug == "custom") {
                OutlinedTextField(
                    value = state.nebiansBaseUrl,
                    onValueChange = viewModel::editNebiansBaseUrl,
                    label = { Text(stringResource(R.string.settings_base_url)) },
                    placeholder = { Text(stringResource(R.string.settings_base_url_hint)) },
                    singleLine = true,
                    shape = UsShapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = state.nebiansKey,
                    onValueChange = viewModel::editNebiansKey,
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = UsShapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.nebians.modelId,
                    onValueChange = viewModel::selectNebiansModel,
                    label = { Text(stringResource(R.string.settings_model)) },
                    placeholder = { Text(stringResource(R.string.settings_model_hint)) },
                    singleLine = true,
                    shape = UsShapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else {
                OutlinedTextField(
                    value = state.nebiansKey,
                    onValueChange = viewModel::editNebiansKey,
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = UsShapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.saveNebiansCredentials(savedLabel) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(
                    onClick = viewModel::testNebiansConnection,
                    enabled = state.nebiansTest !is ConnectionTest.Running,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.settings_test_connection)) }
            }
        } else {
            OutlinedButton(
                onClick = viewModel::testNebiansConnection,
                enabled = state.nebiansTest !is ConnectionTest.Running,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.settings_test_connection)) }
        }

        when (val test = state.nebiansTest) {
            ConnectionTest.Success -> Text(
                stringResource(R.string.settings_connection_ok),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
            is ConnectionTest.Failed -> Text(
                stringResource(R.string.settings_connection_failed, messageFor(test.error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> Unit
        }
    }
}

/** Row that navigates elsewhere. Text column is weighted so long bodies wrap instead of clipping. */
@Composable
private fun SettingsLink(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = UsShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
