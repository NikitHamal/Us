package com.us.copilot.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.Profile
import com.us.copilot.ui.components.LabeledProgress
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

/** Final onboarding page: shows both profiles side by side before saving. */
@Composable
fun OnboardingSummary(me: Profile, partner: Profile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
    ) {
        Text("You are all set", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The coach now has context. You can change any of this later, and every edit is saved " +
                "as a new version so nothing is lost.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SummaryCard(me.name.ifBlank { "You" }, me)
        SummaryCard(partner.name.ifBlank { "Her" }, partner)
    }
}

@Composable
private fun SummaryCard(title: String, profile: Profile) {
    UsCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        LabeledProgress(
            label = stringResource(R.string.profile_completeness),
            progress = profile.completeness,
            trailing = "${(profile.completeness * 100).toInt()}%",
        )
        Spacer(Modifier.height(12.dp))
        SummaryRow(stringResource(R.string.profile_attachment), profile.attachmentStyle.label)
        SummaryRow(stringResource(R.string.profile_conflict), profile.conflictStyle.label)
        SummaryRow(
            stringResource(R.string.profile_love),
            profile.loveLanguages.take(2).joinToString(", ") { it.label }.ifBlank { "Not set" },
        )
        SummaryRow(
            stringResource(R.string.profile_triggers),
            profile.triggers.size.toString() + " selected",
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One selectable love language with its rank badge. Shared by onboarding and the profile editor. */
@Composable
fun LoveLanguageRow(
    language: LoveLanguage,
    rank: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = rank != null
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = tween(180),
        label = "loveScale",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = UsShapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank?.toString() ?: "-",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(language.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    language.example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
