package com.us.copilot.ui.coach

import androidx.annotation.StringRes
import com.us.copilot.R
import com.us.copilot.ai.model.RephraseSet
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.core.util.AppError

/**
 * One entry in the coach conversation.
 *
 * The coach is a chat, not a form. Every exchange stays on screen so you can see how a draft
 * evolved — the third rewrite usually only makes sense next to the first one. Analysis results are
 * first-class message types rather than panels bolted under an input box, which is what lets them
 * scroll away naturally as the conversation moves on.
 */
sealed interface ChatItem {
    val id: Long

    /** Something the user wrote or pasted. Right-aligned. */
    data class UserDraft(
        override val id: Long,
        val text: String,
    ) : ChatItem

    /**
     * Plain coach prose — greetings, explanations, nudges. Carries a string resource rather than
     * literal text so the ViewModel stays free of user-facing copy and stays localisable.
     */
    data class CoachSays(
        override val id: Long,
        @StringRes val textRes: Int,
    ) : ChatItem

    /** A "before you send" verdict card. */
    data class ToneCard(
        override val id: Long,
        val tone: ToneAnalysis,
    ) : ChatItem

    /** Three rewrite options the user can tap to adopt. */
    data class RephraseCardItem(
        override val id: Long,
        val rephrase: RephraseSet,
    ) : ChatItem

    /** Animated three-dot bubble while the model is thinking. */
    data class Thinking(
        override val id: Long,
    ) : ChatItem

    /** A recoverable failure rendered inline, with retry. */
    data class ErrorBubble(
        override val id: Long,
        val error: AppError,
    ) : ChatItem
}

/** Quick-start chips shown when the conversation is empty. */
enum class CoachStarter(
    @StringRes val labelRes: Int,
    @StringRes val promptRes: Int,
) {
    VENT(R.string.coach_starter_vent, R.string.coach_starter_vent_prompt),
    APOLOGISE(R.string.coach_starter_apologise, R.string.coach_starter_apologise_prompt),
    BOUNDARY(R.string.coach_starter_boundary, R.string.coach_starter_boundary_prompt),
    REPAIR(R.string.coach_starter_repair, R.string.coach_starter_repair_prompt),
}
