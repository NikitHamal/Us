package com.us.copilot.ui.coach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.ai.nebians.NebiansCatalog
import com.us.copilot.ai.nebians.NebiansProviderSpec
import com.us.copilot.ai.nebians.ReasoningSupport
import com.us.copilot.domain.repository.NebiansConfig
import com.us.copilot.domain.repository.NebiansEffort
import com.us.copilot.ui.theme.UsShapes

/**
 * Slim model bar pinned above the composer: current provider + model, one tap
 * to switch. Flat by design — no elevation, no shadow.
 */
@Composable
fun ModelBar(
    modelLabel: String,
    cloudEnabled: Boolean,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpenSheet,
        shape = UsShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (cloudEnabled) modelLabel else stringResource(R.string.coach_model_offline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = stringResource(R.string.coach_model_change),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bottom sheet with the full Nebians fleet: provider, model, reasoning config. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    config: NebiansConfig,
    cloudEnabled: Boolean,
    onSetCloudAi: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (NebiansEffort) -> Unit,
    onSelectTemperature: (Float) -> Unit,
    onSelectMaxTokens: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.coach_model_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.coach_model_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.coach_model_cloud_toggle),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.coach_model_cloud_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = cloudEnabled,
                    onCheckedChange = onSetCloudAi,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(4.dp))

            Text(stringResource(R.string.coach_model_provider), style = MaterialTheme.typography.titleSmall)
            NebiansProviderList(
                selectedSlug = config.providerSlug,
                onSelect = onSelectProvider,
            )

            val selected = NebiansCatalog.find(config.providerSlug)
            if (selected != null && selected.models.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.coach_model_model), style = MaterialTheme.typography.titleSmall)
                NebiansModelList(
                    provider = selected,
                    selectedModelId = config.modelId,
                    onSelect = onSelectModel,
                )
            }

            if (selected != null) {
                Spacer(Modifier.height(12.dp))
                ReasoningControls(
                    config = config,
                    provider = selected,
                    onSelectEffort = onSelectEffort,
                    onSelectTemperature = onSelectTemperature,
                    onSelectMaxTokens = onSelectMaxTokens,
                )
                CapabilityNote(provider = selected)
            }
        }
    }
}

/** Provider radio list, shared by the coach sheet and Settings. */
@Composable
fun NebiansProviderList(
    selectedSlug: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        NebiansCatalog.providers.forEach { provider ->
            ProviderRow(
                provider = provider,
                selected = provider.slug == selectedSlug,
                onClick = { onSelect(provider.slug) },
            )
        }
    }
}

/** Model radio list for one provider, shared by the coach sheet and Settings. */
@Composable
fun NebiansModelList(
    provider: NebiansProviderSpec,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        provider.models.forEach { model ->
            ModelRow(
                label = model.label,
                note = model.note,
                selected = selectedModelId.isBlank() && model.id == provider.defaultModel ||
                    selectedModelId.equals(model.id, ignoreCase = true),
                onClick = { onSelect(model.id) },
            )
        }
    }
}

@Composable
private fun ProviderRow(provider: NebiansProviderSpec, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(UsShapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(provider.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = provider.freeNote.ifBlank {
                if (provider.keyRequired) stringResource(R.string.coach_model_needs_key) else ""
            }
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ModelRow(label: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(UsShapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (note.isNotBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Reasoning controls, shared with Settings. Only the controls the selected
 * provider actually supports are shown: effort for TryingOpen-style, sliders
 * for official-style APIs, nothing for fixed guest scrapers.
 */
@Composable
fun ReasoningControls(
    config: NebiansConfig,
    provider: NebiansProviderSpec,
    onSelectEffort: (NebiansEffort) -> Unit,
    onSelectTemperature: (Float) -> Unit,
    onSelectMaxTokens: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (provider.reasoning) {
            ReasoningSupport.EFFORT -> {
                Text(stringResource(R.string.coach_model_effort), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    NebiansEffort.entries.forEachIndexed { index, effort ->
                        SegmentedButton(
                            selected = config.effort == effort,
                            onClick = { onSelectEffort(effort) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = NebiansEffort.entries.size),
                            label = { Text(effort.label) },
                        )
                    }
                }
            }
            ReasoningSupport.TEMPERATURE -> {
                Text(
                    stringResource(R.string.coach_model_temperature, config.temperature),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = config.temperature.coerceIn(0f, 1f),
                    onValueChange = onSelectTemperature,
                    valueRange = 0f..1f,
                )
                Text(stringResource(R.string.coach_model_max_tokens), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaxTokenOptions.forEach { option ->
                        FilterChip(
                            selected = config.maxTokens == option,
                            onClick = { onSelectMaxTokens(option) },
                            label = { Text(option.toString()) },
                        )
                    }
                }
            }
            ReasoningSupport.NONE -> Unit
        }
    }
}

private val MaxTokenOptions = listOf(256, 512, 900, 1500, 2500)

@Composable
private fun CapabilityNote(provider: NebiansProviderSpec) {
    val bits = buildList {
        if (provider.supportsFiles) add(stringResource(R.string.coach_model_cap_files))
        if (provider.models.any { it.thinking }) add(stringResource(R.string.coach_model_cap_thinking))
        if (provider.models.any { it.webSearch }) add(stringResource(R.string.coach_model_cap_search))
    }
    if (bits.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(
        bits.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (provider.keyRequired && provider.slug != "custom") {
        Text(
            stringResource(R.string.coach_model_key_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
