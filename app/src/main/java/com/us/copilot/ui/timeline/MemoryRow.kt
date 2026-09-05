package com.us.copilot.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.core.model.Memory
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.ui.theme.UsShapes

/** One moment in the timeline. Compact, scannable, accessible. */
@Composable
fun MemoryRow(
    memory: Memory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleResolved: ((Boolean) -> Unit)? = null,
) {
    val emotionLabel = stringResource(R.string.cd_emotion, memory.emotion.label)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = UsShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp)) {
            Text(
                text = memory.emotion.emoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = emotionLabel },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = memory.speaker.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = TimeUtils.relative(memory.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = memory.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (memory.isUnresolved) {
                        UnresolvedBadge(
                            onClick = onToggleResolved?.let { { it(true) } },
                        )
                    }
                    Text(
                        text = stringResource(R.string.timeline_source, memory.source.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    memory.tags.take(2).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnresolvedBadge(onClick: (() -> Unit)?) {
    val label = stringResource(R.string.cd_unresolved)
    if (onClick == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Circle,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(8.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else {
        AssistChip(
            onClick = onClick,
            label = { Text(stringResource(R.string.timeline_mark_resolved)) },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}
