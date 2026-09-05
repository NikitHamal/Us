package com.us.copilot.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.ai.model.RiskLevel
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.core.model.ProviderId
import com.us.copilot.ui.components.LabeledProgress
import com.us.copilot.ui.components.UsCard

/** Verdict card: risk level, harshness meter, horsemen, triggers. */
@Composable
fun ToneResultCard(tone: ToneAnalysis, modifier: Modifier = Modifier) {
    val accent = riskColor(tone.riskLevel)
    val riskLabel = stringResource(
        when (tone.riskLevel) {
            RiskLevel.LOW -> R.string.coach_verdict_low
            RiskLevel.MEDIUM -> R.string.coach_verdict_medium
            RiskLevel.HIGH -> R.string.coach_verdict_high
        },
    )
    val riskDescription = stringResource(R.string.cd_risk_level, tone.riskLevel.label)

    UsCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (tone.riskLevel) {
                    RiskLevel.LOW -> Icons.Filled.CheckCircle
                    RiskLevel.MEDIUM -> Icons.Filled.Warning
                    RiskLevel.HIGH -> Icons.Filled.Error
                },
                contentDescription = null,
                tint = accent,
            )
            Text(
                text = riskLabel,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .semantics { contentDescription = riskDescription },
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(tone.summary, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))
        LabeledProgress(
            label = stringResource(R.string.coach_harshness),
            progress = tone.harshnessScore / 100f,
            trailing = "${tone.harshnessScore}/100",
            color = accent,
        )

        if (tone.detectedHorsemen.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.coach_horsemen_found),
                style = MaterialTheme.typography.titleSmall,
            )
            tone.detectedHorsemen.forEach { hit ->
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        "${hit.horseman.label} · \u201C${hit.evidence}\u201D",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.coach_horsemen_antidote, hit.horseman.antidote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (tone.triggerHits.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.coach_triggers_found),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tone.triggerHits.take(3).forEach { trigger ->
                    AssistChip(onClick = {}, label = { Text(trigger) }, enabled = false)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ProviderBadge(tone.provider, tone.confidence)
    }
}

@Composable
fun ProviderBadge(provider: ProviderId, confidence: Float, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (provider) {
            ProviderId.OFFLINE -> R.string.coach_provider_offline
            ProviderId.NEBIANS -> R.string.coach_provider_nebians
            ProviderId.CLOUD -> R.string.coach_provider_cloud
        },
    )
    val description = stringResource(R.string.cd_provider_badge, provider.label)
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.height(14.dp),
        )
        Text(
            text = "$label · ${(confidence * 100).toInt()}% confidence",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
fun riskColor(risk: RiskLevel): Color = when (risk) {
    RiskLevel.LOW -> MaterialTheme.colorScheme.tertiary
    RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
}
