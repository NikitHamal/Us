package com.us.copilot.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Speaker
import com.us.copilot.ui.components.ScaleSlider
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

@Composable
fun JournalScreen(
    contentPadding: PaddingValues,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    prefillText: String? = null,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(prefillText) { if (!prefillText.isNullOrBlank()) viewModel.prefill(prefillText) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UsDimens.screenPadding)
                .padding(bottom = contentPadding.calculateBottomPadding() + UsDimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(UsDimens.gutter),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text(stringResource(R.string.journal_hint)) },
                minLines = 5,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(title = stringResource(R.string.journal_emotion))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Emotion.entries.forEach { emotion ->
                    FilterChip(
                        selected = state.emotion == emotion,
                        onClick = { viewModel.onEmotionChange(emotion) },
                        label = { Text("${emotion.emoji} ${emotion.label}") },
                        shape = UsShapes.small,
                    )
                }
            }

            ScaleSlider(
                label = stringResource(R.string.journal_intensity),
                value = state.intensity,
                onValueChange = viewModel::onIntensityChange,
                valueLabels = listOf("Barely", "Mild", "Noticeable", "Strong", "Overwhelming"),
            )

            SectionHeader(title = stringResource(R.string.share_who_said))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Speaker.entries.forEach { speaker ->
                    FilterChip(
                        selected = state.speaker == speaker,
                        onClick = { viewModel.onSpeakerChange(speaker) },
                        label = { Text(speaker.label) },
                        shape = UsShapes.small,
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.journal_tags))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.suggestedTags.forEach { tag ->
                    FilterChip(
                        selected = tag in state.tags,
                        onClick = { viewModel.toggleTag(tag) },
                        label = { Text("#$tag") },
                        shape = UsShapes.small,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.journal_unresolved),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(checked = state.isUnresolved, onCheckedChange = viewModel::onUnresolvedChange)
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
