package com.us.copilot.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Speaker
import com.us.copilot.ui.coach.RephraseCard
import com.us.copilot.ui.coach.ToneResultCard
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

/** Bottom sheet shown when text is shared into Us. */
@Composable
fun ShareSheet(
    state: ShareUiState,
    onSpeakerChange: (Speaker) -> Unit,
    onEmotionChange: (Emotion) -> Unit,
    onUnresolvedChange: (Boolean) -> Unit,
    onRephrase: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UsDimens.screenPadding)
                .padding(bottom = UsDimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(UsDimens.gutter),
        ) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.headlineSmall)

            if (!state.hasText) {
                Text(
                    stringResource(R.string.share_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_close))
                }
                return@Column
            }

            androidx.compose.material3.Surface(
                shape = UsShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    state.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Text(stringResource(R.string.share_who_said), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Speaker.entries.forEach { speaker ->
                    FilterChip(
                        selected = state.speaker == speaker,
                        onClick = { onSpeakerChange(speaker) },
                        label = { Text(speaker.label) },
                        shape = UsShapes.small,
                    )
                }
            }

            Text(stringResource(R.string.journal_emotion), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Emotion.entries.forEach { emotion ->
                    FilterChip(
                        selected = state.emotion == emotion,
                        onClick = { onEmotionChange(emotion) },
                        label = { Text("${emotion.emoji} ${emotion.label}") },
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
                Switch(checked = state.isUnresolved, onCheckedChange = onUnresolvedChange)
            }

            if (state.isAnalyzing) {
                LoadingState(Modifier.fillMaxWidth().padding(24.dp))
            }

            state.tone?.let { ToneResultCard(it) }
            state.rephrase?.let { RephraseCard(rephrase = it, onUseRewrite = {}) }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onRephrase, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.coach_rephrase))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_action_save))
                }
            }
        }
    }
}
