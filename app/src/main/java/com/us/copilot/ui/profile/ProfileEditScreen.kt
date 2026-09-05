package com.us.copilot.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.core.model.AttachmentStyle
import com.us.copilot.core.model.ConflictStyle
import com.us.copilot.core.model.LoveLanguage
import com.us.copilot.core.model.ProfileOwner
import com.us.copilot.core.model.ProfileVocabulary
import com.us.copilot.ui.components.BigFiveSliders
import com.us.copilot.ui.components.ChipMultiSelect
import com.us.copilot.ui.components.RadioOptionList
import com.us.copilot.ui.components.SectionHeader
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.onboarding.LoveLanguageRow
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

@Composable
fun ProfileEditScreen(
    owner: ProfileOwner,
    contentPadding: PaddingValues,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(owner) { viewModel.load(owner) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    val draft = state.draft

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (owner == ProfileOwner.ME) R.string.profile_me else R.string.profile_partner,
                        ),
                    )
                },
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
            verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::editName,
                label = { Text(stringResource(R.string.onboarding_name_label)) },
                singleLine = true,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_attachment))
                RadioOptionList(
                    options = AttachmentStyle.entries,
                    selected = draft.attachmentStyle,
                    label = { it.label },
                    description = { it.blurb },
                    onSelect = viewModel::editAttachment,
                )
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_conflict))
                RadioOptionList(
                    options = ConflictStyle.entries,
                    selected = draft.conflictStyle,
                    label = { it.label },
                    description = { it.blurb },
                    onSelect = viewModel::editConflict,
                )
            }

            UsCard {
                SectionHeader(
                    title = stringResource(R.string.profile_love),
                    subtitle = stringResource(R.string.onboarding_love_body),
                )
                LoveLanguage.entries.forEach { language ->
                    val rank = draft.loveLanguages.indexOf(language)
                    LoveLanguageRow(
                        language = language,
                        rank = if (rank >= 0) rank + 1 else null,
                        onClick = { viewModel.toggleLoveLanguage(language) },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_triggers))
                ChipMultiSelect(
                    options = (ProfileVocabulary.triggers + draft.triggers).distinct(),
                    selected = draft.triggers.toSet(),
                    onToggle = viewModel::toggleTrigger,
                )
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_soothers))
                ChipMultiSelect(
                    options = (ProfileVocabulary.soothers + draft.soothers).distinct(),
                    selected = draft.soothers.toSet(),
                    onToggle = viewModel::toggleSoother,
                )
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_stress))
                ChipMultiSelect(
                    options = (ProfileVocabulary.stressPatterns + draft.stressPatterns).distinct(),
                    selected = draft.stressPatterns.toSet(),
                    onToggle = viewModel::toggleStress,
                )
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_comm))
                ChipMultiSelect(
                    options = (ProfileVocabulary.commPreferences + draft.commPreferences).distinct(),
                    selected = draft.commPreferences.toSet(),
                    onToggle = viewModel::toggleComm,
                )
            }

            UsCard {
                SectionHeader(title = stringResource(R.string.profile_bigfive))
                BigFiveSliders(
                    value = draft.bigFive,
                    onChange = viewModel::editBigFive,
                )
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::editNote,
                label = { Text(stringResource(R.string.profile_note)) },
                minLines = 3,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::save,
                enabled = draft.name.isNotBlank() && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
