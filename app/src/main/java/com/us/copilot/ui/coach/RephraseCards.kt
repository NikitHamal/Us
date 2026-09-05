package com.us.copilot.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsShapes

/** The three rewrite voices plus the NVC skeleton and love-language tip. */
@Composable
fun RephraseCard(
    rephrase: RephraseSet,
    onUseRewrite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    UsCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.coach_options))

        rephrase.options.forEach { option ->
            Surface(
                shape = UsShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.style.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(option.text)) },
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                            )
                        }
                    }
                    Text(option.text, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        option.why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onUseRewrite(option.text) }) {
                        Text(stringResource(R.string.action_edit))
                    }
                }
            }
        }

        rephrase.loveLanguageTip?.let { tip ->
            Surface(
                shape = UsShapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(Modifier.padding(14.dp)) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.height(20.dp),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            stringResource(R.string.coach_love_tip, tip.language.label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            tip.suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        rephrase.nvc?.let { nvc ->
            Text(
                stringResource(R.string.coach_nvc_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            NvcLine("Observation", nvc.observation)
            NvcLine("Feeling", nvc.feeling)
            NvcLine("Need", nvc.need)
            NvcLine("Request", nvc.request)
        }

        Spacer(Modifier.height(10.dp))
        ProviderBadge(rephrase.provider, rephrase.confidence)
    }
}

@Composable
private fun NvcLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
