package com.us.copilot.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

/** Bottom sheet holding every timeline filter. */
@Composable
fun TimelineFilterSheet(
    state: TimelineUiState,
    viewModel: TimelineViewModel,
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
            Text(stringResource(R.string.timeline_filter), style = MaterialTheme.typography.titleLarge)

            FilterGroup(title = stringResource(R.string.journal_emotion)) {
                Emotion.entries.forEach { emotion ->
                    FilterChip(
                        selected = emotion in state.filter.emotions,
                        onClick = { viewModel.toggleEmotion(emotion) },
                        label = { Text("${emotion.emoji} ${emotion.label}") },
                        shape = UsShapes.small,
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.share_who_said)) {
                Speaker.entries.forEach { speaker ->
                    FilterChip(
                        selected = speaker in state.filter.speakers,
                        onClick = { viewModel.toggleSpeaker(speaker) },
                        label = { Text(speaker.label) },
                        shape = UsShapes.small,
                    )
                }
            }

            FilterGroup(title = stringResource(R.string.timeline_source, "").trim()) {
                MemorySource.entries.forEach { source ->
                    FilterChip(
                        selected = source in state.filter.sources,
                        onClick = { viewModel.toggleSource(source) },
                        label = { Text(source.label) },
                        shape = UsShapes.small,
                    )
                }
            }

            if (state.availableTags.isNotEmpty()) {
                FilterGroup(title = stringResource(R.string.journal_tags)) {
                    state.availableTags.forEach { tag ->
                        FilterChip(
                            selected = state.filter.tag == tag,
                            onClick = {
                                viewModel.selectTag(if (state.filter.tag == tag) null else tag)
                            },
                            label = { Text("#$tag") },
                            shape = UsShapes.small,
                        )
                    }
                }
            }

            FilterChip(
                selected = state.filter.onlyUnresolved,
                onClick = viewModel::toggleUnresolvedOnly,
                label = { Text(stringResource(R.string.timeline_filter_unresolved)) },
                shape = UsShapes.small,
            )

            TextButton(onClick = viewModel::clearFilters) {
                Text(stringResource(R.string.timeline_filter_clear))
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}
