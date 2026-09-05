package com.us.copilot.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.us.copilot.R
import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.ProfileVocabulary
import com.us.copilot.ui.components.BigFiveSliders
import com.us.copilot.ui.components.ChipMultiSelect
import com.us.copilot.ui.components.RadioOptionList
import com.us.copilot.ui.theme.UsDimens

/** Renders the body of a single onboarding page. Navigation chrome lives in OnboardingScreen. */
@Composable
fun OnboardingPageContent(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val profile = state.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
    ) {
        when (state.page) {
            OnboardingPage.WELCOME -> Intro(
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                art = { TwoHeartsArt() },
            )
            OnboardingPage.PRIVACY -> Intro(
                title = stringResource(R.string.onboarding_privacy_title),
                body = stringResource(R.string.onboarding_privacy_body),
                art = { PrivacyShieldArt() },
            )
            OnboardingPage.ETHICS -> Intro(
                title = stringResource(R.string.onboarding_ethics_title),
                body = stringResource(R.string.onboarding_ethics_body),
                art = { BalanceArt() },
            )

            OnboardingPage.ME_NAME, OnboardingPage.PARTNER_NAME -> NamePage(
                value = profile.name,
                isPartner = state.page.isForPartner,
                onChange = viewModel::updateName,
            )

            OnboardingPage.ME_ATTACHMENT, OnboardingPage.PARTNER_ATTACHMENT -> QuestionPage(
                title = stringResource(R.string.onboarding_attachment_title),
                body = stringResource(R.string.onboarding_attachment_body),
            ) {
                RadioOptionList(
                    options = AttachmentStyle.entries,
                    selected = profile.attachmentStyle,
                    label = { it.label },
                    description = { it.blurb },
                    onSelect = viewModel::updateAttachment,
                )
            }

            OnboardingPage.ME_LOVE, OnboardingPage.PARTNER_LOVE -> QuestionPage(
                title = stringResource(R.string.onboarding_love_title),
                body = stringResource(R.string.onboarding_love_body),
            ) {
                LoveLanguageRanker(
                    ranked = profile.loveLanguages,
                    onToggle = viewModel::toggleLoveLanguage,
                )
            }

            OnboardingPage.ME_CONFLICT, OnboardingPage.PARTNER_CONFLICT -> QuestionPage(
                title = stringResource(R.string.onboarding_conflict_title),
                body = stringResource(R.string.onboarding_conflict_body),
            ) {
                RadioOptionList(
                    options = ConflictStyle.entries,
                    selected = profile.conflictStyle,
                    label = { it.label },
                    description = { it.blurb },
                    onSelect = viewModel::updateConflict,
                )
            }

            OnboardingPage.ME_TRIGGERS, OnboardingPage.PARTNER_TRIGGERS -> QuestionPage(
                title = stringResource(R.string.onboarding_triggers_title),
                body = stringResource(R.string.onboarding_triggers_body),
            ) {
                ChipMultiSelect(
                    options = ProfileVocabulary.triggers,
                    selected = profile.triggers.toSet(),
                    onToggle = viewModel::toggleTrigger,
                )
            }

            OnboardingPage.ME_SOOTHERS, OnboardingPage.PARTNER_SOOTHERS -> QuestionPage(
                title = stringResource(R.string.onboarding_soothers_title),
                body = stringResource(R.string.onboarding_soothers_body),
            ) {
                ChipMultiSelect(
                    options = ProfileVocabulary.soothers,
                    selected = profile.soothers.toSet(),
                    onToggle = viewModel::toggleSoother,
                )
            }

            OnboardingPage.ME_BIGFIVE, OnboardingPage.PARTNER_BIGFIVE -> QuestionPage(
                title = stringResource(R.string.onboarding_bigfive_title),
                body = stringResource(R.string.onboarding_bigfive_body),
            ) {
                BigFiveSliders(profile.bigFive, viewModel::updateBigFive)
            }

            OnboardingPage.ME_STRESS, OnboardingPage.PARTNER_STRESS -> QuestionPage(
                title = stringResource(R.string.onboarding_stress_title),
                body = stringResource(R.string.onboarding_stress_body),
            ) {
                ChipMultiSelect(
                    options = ProfileVocabulary.stressPatterns,
                    selected = profile.stressPatterns.toSet(),
                    onToggle = viewModel::toggleStress,
                )
            }

            OnboardingPage.ME_COMM, OnboardingPage.PARTNER_COMM -> QuestionPage(
                title = stringResource(R.string.onboarding_comm_title),
                body = stringResource(R.string.onboarding_comm_body),
            ) {
                ChipMultiSelect(
                    options = ProfileVocabulary.commPreferences,
                    selected = profile.commPreferences.toSet(),
                    onToggle = viewModel::toggleComm,
                )
            }

            OnboardingPage.SUMMARY -> OnboardingSummary(state.me, state.partner)
        }
    }
}

@Composable
private fun Intro(title: String, body: String, art: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        art()
        Spacer(Modifier.height(UsDimens.sectionSpacing))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = UsDimens.gutter),
        )
    }
}

@Composable
private fun QuestionPage(title: String, body: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(UsDimens.gutter)) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        content()
    }
}

@Composable
private fun NamePage(value: String, isPartner: Boolean, onChange: (String) -> Unit) {
    QuestionPage(
        title = stringResource(
            if (isPartner) R.string.onboarding_partner_title else R.string.onboarding_me_title,
        ),
        body = stringResource(
            if (isPartner) R.string.onboarding_partner_caveat else R.string.onboarding_welcome_body,
        ),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            placeholder = {
                Text(
                    stringResource(
                        if (isPartner) R.string.onboarding_name_hint_partner
                        else R.string.onboarding_name_hint_me,
                    ),
                )
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LoveLanguageRanker(ranked: List<LoveLanguage>, onToggle: (LoveLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LoveLanguage.entries.forEach { language ->
            val rank = ranked.indexOf(language)
            LoveLanguageRow(
                language = language,
                rank = if (rank >= 0) rank + 1 else null,
                onClick = { onToggle(language) },
            )
        }
    }
}
