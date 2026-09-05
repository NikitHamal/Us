package com.us.copilot.ui.journal

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.ui.components.LoadingState
import com.us.copilot.ui.components.ScaleSlider
import com.us.copilot.ui.components.UsCard
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.theme.UsShapes

@Composable
fun CheckInScreen(
    contentPadding: PaddingValues,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.check_in_title)) },
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
        if (state.isLoading) {
            LoadingState(Modifier.padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UsDimens.screenPadding)
                .padding(bottom = contentPadding.calculateBottomPadding() + UsDimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(UsDimens.gutter),
        ) {
            UsCard {
                ScaleSlider(
                    label = stringResource(R.string.check_in_mood),
                    value = state.mood,
                    onValueChange = viewModel::onMood,
                    valueLabels = MOOD_LABELS,
                )
                ScaleSlider(
                    label = stringResource(R.string.check_in_energy),
                    value = state.energy,
                    onValueChange = viewModel::onEnergy,
                    valueLabels = ENERGY_LABELS,
                    modifier = Modifier.padding(top = UsDimens.itemSpacing),
                )
                ScaleSlider(
                    label = stringResource(R.string.check_in_connection),
                    value = state.connection,
                    onValueChange = viewModel::onConnection,
                    valueLabels = CONNECTION_LABELS,
                    modifier = Modifier.padding(top = UsDimens.itemSpacing),
                )
            }

            OutlinedTextField(
                value = state.gratitude,
                onValueChange = viewModel::onGratitude,
                label = { Text(stringResource(R.string.check_in_gratitude)) },
                minLines = 2,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.check_in_note)) },
                minLines = 3,
                shape = UsShapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

private val MOOD_LABELS = listOf("Rough", "Low", "Okay", "Good", "Great")
private val ENERGY_LABELS = listOf("Empty", "Low", "Steady", "Charged", "Buzzing")
private val CONNECTION_LABELS = listOf("Distant", "Strained", "Fine", "Close", "In sync")
