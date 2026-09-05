package com.us.copilot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.us.copilot.core.model.BigFive
import com.us.copilot.ui.theme.UsDimens

/**
 * The five OCEAN sliders, shared by onboarding and the profile editor so the two can never drift.
 *
 * Domain scores are 0..100; the UI works in a 1..5 scale because a rough sketch is all we ask for.
 */
@Composable
fun BigFiveSliders(
    value: BigFive,
    onChange: (BigFive) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UsDimens.itemSpacing),
    ) {
        ScaleSlider(
            label = "Openness",
            value = value.openness.toStep(),
            onValueChange = { step -> onChange(value.copy(openness = step.toScore())) },
            valueLabels = OPENNESS_LABELS,
        )
        ScaleSlider(
            label = "Conscientiousness",
            value = value.conscientiousness.toStep(),
            onValueChange = { step -> onChange(value.copy(conscientiousness = step.toScore())) },
            valueLabels = CONSCIENTIOUSNESS_LABELS,
        )
        ScaleSlider(
            label = "Extraversion",
            value = value.extraversion.toStep(),
            onValueChange = { step -> onChange(value.copy(extraversion = step.toScore())) },
            valueLabels = EXTRAVERSION_LABELS,
        )
        ScaleSlider(
            label = "Agreeableness",
            value = value.agreeableness.toStep(),
            onValueChange = { step -> onChange(value.copy(agreeableness = step.toScore())) },
            valueLabels = AGREEABLENESS_LABELS,
        )
        ScaleSlider(
            label = "Emotional reactivity",
            value = value.neuroticism.toStep(),
            onValueChange = { step -> onChange(value.copy(neuroticism = step.toScore())) },
            valueLabels = REACTIVITY_LABELS,
        )
    }
}

/** 0..100 score to a 1..5 slider step, rounding to the nearest bucket. */
private fun Int.toStep(): Int = ((this + 10) / 20).coerceIn(1, 5)

/** 1..5 slider step back to a 0..100 score. */
private fun Int.toScore(): Int = (this * 20).coerceIn(0, 100)

private val OPENNESS_LABELS = listOf("Set in her ways", "Cautious", "Balanced", "Curious", "Craves the new")
private val CONSCIENTIOUSNESS_LABELS = listOf("Go with the flow", "Relaxed", "Balanced", "Organised", "Plans everything")
private val EXTRAVERSION_LABELS = listOf("Very private", "Reserved", "Balanced", "Sociable", "Needs people")
private val AGREEABLENESS_LABELS = listOf("Blunt", "Direct", "Balanced", "Warm", "Puts others first")
private val REACTIVITY_LABELS = listOf("Unshakeable", "Steady", "Balanced", "Sensitive", "Feels everything")
