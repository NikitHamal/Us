package com.us.copilot.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.core.model.HorsemanCount
import com.us.copilot.core.model.TriggerCount
import com.us.copilot.core.util.TimeUtils
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.components.UsCard

@Composable
fun TriggersCard(triggers: List<TriggerCount>, modifier: Modifier = Modifier) {
    UsCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.insights_triggers))
        val max = triggers.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        triggers.forEach { trigger ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(trigger.trigger, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${trigger.count}×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { trigger.count.toFloat() / max },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    "Last seen ${TimeUtils.relative(trigger.lastSeen)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun HorsemenCard(horsemen: List<HorsemanCount>, modifier: Modifier = Modifier) {
    UsCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.insights_horsemen))
        if (horsemen.isEmpty()) {
            Text(
                stringResource(R.string.insights_horsemen_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            return@UsCard
        }
        horsemen.forEach { entry ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(entry.horseman.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${entry.count}×",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    entry.horseman.antidote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Simple sparkline for the connection trend. Purely decorative; the numbers are read out too. */
@Composable
fun ConnectionTrendCard(values: List<Float>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val description = "Connection trend, latest value ${values.last().toInt()} out of 5"

    UsCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.insights_connection_trend))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = description },
        ) {
            val maxValue = 5f
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height - (value / maxValue) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 6f, cap = StrokeCap.Round),
            )
            values.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height - (value / maxValue) * size.height
                drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
            }
        }
    }
}

@Composable
fun PatternReportCard(
    observations: List<String>,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    UsCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.insights_ai_patterns))
        observations.forEach { observation ->
            Text(
                "· $observation",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("What might help", style = MaterialTheme.typography.titleSmall)
            suggestions.forEach { suggestion ->
                Text(
                    "· $suggestion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}
