package com.us.copilot.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.ui.theme.UsDimens

@Composable
fun OnboardingScreen(
    contentPadding: PaddingValues,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    // Predictive back: step backwards through the quiz instead of leaving the app.
    BackHandler(enabled = state.page != OnboardingPage.WELCOME) { viewModel.back() }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
                .padding(horizontal = UsDimens.screenPadding),
        ) {
            Spacer(Modifier.height(UsDimens.gutter))

            if (!state.page.isIntro) {
                LinearProgressIndicator(
                    progress = { state.stepNumber.toFloat() / state.totalSteps },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Text(
                    text = stringResource(
                        R.string.onboarding_step,
                        state.stepNumber.coerceAtMost(state.totalSteps),
                        state.totalSteps,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            AnimatedContent(
                targetState = state.page,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 4 * offset } + fadeIn(tween(280)))
                        .togetherWith(
                            slideOutHorizontally(tween(280)) { -it / 4 * offset } + fadeOut(tween(200)),
                        )
                },
                label = "onboardingPage",
                modifier = Modifier.weight(1f).padding(top = UsDimens.sectionSpacing),
            ) { _ ->
                OnboardingPageContent(state = state, viewModel = viewModel)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = UsDimens.gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.page.isIntro && state.page != OnboardingPage.SUMMARY) {
                    TextButton(onClick = viewModel::skipToSummary) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = viewModel::next,
                    enabled = state.canContinue && !state.isSaving,
                ) {
                    Text(
                        stringResource(
                            if (state.page == OnboardingPage.SUMMARY) {
                                R.string.onboarding_finish
                            } else {
                                R.string.action_continue
                            },
                        ),
                    )
                }
            }
        }
    }
}
