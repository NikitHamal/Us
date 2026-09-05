package com.us.copilot.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.R
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.core.util.Outcome
import com.us.copilot.ai.agent.AgentHistoryEntry
import com.us.copilot.domain.usecase.AskCoachUseCase
import com.us.copilot.domain.usecase.BeforeYouSendUseCase
import com.us.copilot.domain.usecase.RephraseUseCase
import com.us.copilot.domain.usecase.SaveMemoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachUiState(
    val items: List<ChatItem> = emptyList(),
    val draft: String = "",
    val isAnalyzing: Boolean = false,
    val isRephrasing: Boolean = false,
    val isAsking: Boolean = false,
    val savedMessage: Boolean = false,
    /** Last analysed text, kept so "save as moment" works after the input clears. */
    val lastAnalyzed: String = "",
    val lastTone: ToneAnalysis? = null,
) {
    val hasInput: Boolean get() = draft.trim().length >= 2
    val isBusy: Boolean get() = isAnalyzing || isRephrasing || isAsking
    val isEmpty: Boolean get() = items.isEmpty()
    val canSaveMoment: Boolean get() = lastTone != null && lastAnalyzed.isNotBlank()
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val beforeYouSend: BeforeYouSendUseCase,
    private val askCoach: AskCoachUseCase,
    private val rephraseUseCase: RephraseUseCase,
    private val saveMemory: SaveMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    private var nextId = 1L
    private fun id(): Long = nextId++

    fun onDraftChange(value: String) = _uiState.update { it.copy(draft = value) }

    /**
     * Open-ended question for the agent.
     *
     * Separate from [analyze]: that checks a draft the user intends to send, this asks the coach
     * something. Both share the transcript so the conversation reads as one thread.
     */
    fun ask() {
        val text = _uiState.value.draft.trim()
        if (text.length < 2 || _uiState.value.isBusy) return

        val thinkingId = id()
        val history = _uiState.value.items.toHistory()

        _uiState.update {
            it.copy(
                items = it.items +
                    ChatItem.UserDraft(id(), text) +
                    ChatItem.Thinking(thinkingId),
                draft = "",
                isAsking = true,
            )
        }

        viewModelScope.launch {
            val replacement = when (val result = askCoach(text, history)) {
                is Outcome.Success -> ChatItem.AgentReply(
                    id = id(),
                    text = result.value.reply,
                    steps = result.value.steps,
                    hitIterationCap = result.value.hitIterationCap,
                )
                is Outcome.Failure -> ChatItem.ErrorBubble(id(), result.error)
            }
            _uiState.update {
                it.copy(
                    items = it.items.replaceThinking(thinkingId, listOf(replacement)),
                    isAsking = false,
                )
            }
        }
    }

    /** Fills the composer from a starter chip without sending, so the user can edit first. */
    fun applyStarter(prompt: String) = _uiState.update { it.copy(draft = prompt) }

    /** Prefills and immediately analyses text arriving from a share intent. */
    fun prefill(text: String) {
        _uiState.update { it.copy(draft = text) }
        analyze()
    }

    /** Sends the draft as a user turn and asks for a "before you send" verdict. */
    fun analyze() {
        val text = _uiState.value.draft.trim()
        if (text.length < 2 || _uiState.value.isBusy) return

        val thinkingId = id()
        _uiState.update {
            it.copy(
                items = it.items +
                    ChatItem.UserDraft(id(), text) +
                    ChatItem.Thinking(thinkingId),
                draft = "",
                isAnalyzing = true,
                lastAnalyzed = text,
            )
        }

        viewModelScope.launch {
            when (val result = beforeYouSend(text)) {
                is Outcome.Success -> {
                    val value = result.value
                    val additions = buildList {
                        // A short spoken line before the card keeps the exchange feeling like a
                        // conversation rather than a form silently emitting a result panel.
                        add(
                            ChatItem.CoachSays(
                                id(),
                                if (value.tone.isSafeToSend) {
                                    R.string.coach_ack_safe
                                } else {
                                    R.string.coach_ack_risky
                                },
                            ),
                        )
                        add(ChatItem.ToneCard(id(), value.tone))
                        value.rephrase?.let { add(ChatItem.RephraseCardItem(id(), it)) }
                    }
                    _uiState.update {
                        it.copy(
                            items = it.items.replaceThinking(thinkingId, additions),
                            isAnalyzing = false,
                            lastTone = value.tone,
                        )
                    }
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(
                        items = it.items.replaceThinking(
                            thinkingId,
                            listOf(ChatItem.ErrorBubble(id(), result.error)),
                        ),
                        isAnalyzing = false,
                    )
                }
            }
        }
    }

    /**
     * Asks for rewrites. Uses the composer text when present, otherwise the last analysed draft —
     * so tapping the sparkle after a verdict rewrites what you just checked.
     */
    fun rephrase() {
        val state = _uiState.value
        val text = state.draft.trim().ifBlank { state.lastAnalyzed }
        if (text.length < 2 || state.isBusy) return

        val thinkingId = id()
        val userTurn = if (state.draft.isNotBlank()) {
            listOf(ChatItem.UserDraft(id(), text))
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                items = it.items + userTurn + ChatItem.Thinking(thinkingId),
                draft = "",
                isRephrasing = true,
                lastAnalyzed = text,
            )
        }

        viewModelScope.launch {
            when (val result = rephraseUseCase(text)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        items = it.items.replaceThinking(
                            thinkingId,
                            listOf(
                                ChatItem.CoachSays(id(), R.string.coach_ack_rewrite),
                                ChatItem.RephraseCardItem(id(), result.value),
                            ),
                        ),
                        isRephrasing = false,
                    )
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(
                        items = it.items.replaceThinking(
                            thinkingId,
                            listOf(ChatItem.ErrorBubble(id(), result.error)),
                        ),
                        isRephrasing = false,
                    )
                }
            }
        }
    }

    /** Drops a chosen rewrite back into the composer so it can be tweaked before use. */
    fun useRewrite(text: String) = _uiState.update { it.copy(draft = text) }

    fun retry() {
        val text = _uiState.value.lastAnalyzed
        if (text.isBlank()) return
        _uiState.update { it.copy(draft = text, items = it.items.dropLastWhile { i -> i is ChatItem.ErrorBubble }) }
        analyze()
    }

    /** Saves the analysed text into the timeline so it feeds the pattern engine. */
    fun saveAsMemory(speaker: Speaker = Speaker.ME, source: MemorySource = MemorySource.MANUAL) {
        val state = _uiState.value
        val text = state.lastAnalyzed.ifBlank { state.draft.trim() }
        if (text.isEmpty()) return

        viewModelScope.launch {
            saveMemory(
                Memory(
                    text = text,
                    emotion = state.lastTone?.primaryEmotion ?: Emotion.NEUTRAL,
                    intensity = intensityFrom(state.lastTone),
                    timestamp = System.currentTimeMillis(),
                    source = source,
                    speaker = speaker,
                    tags = state.lastTone?.detectedHorsemen
                        ?.map { it.horseman.label.lowercase() }.orEmpty(),
                    isUnresolved = state.lastTone?.isSafeToSend == false,
                ),
            )
            _uiState.update { it.copy(savedMessage = true) }
        }
    }

    fun consumeSavedMessage() = _uiState.update { it.copy(savedMessage = false) }

    fun clear() {
        _uiState.value = CoachUiState()
    }

    private fun intensityFrom(tone: ToneAnalysis?): Int = when {
        tone == null -> 3
        tone.harshnessScore >= 70 -> 5
        tone.harshnessScore >= 45 -> 4
        tone.harshnessScore >= 20 -> 3
        else -> 2
    }
}

/**
 * Flattens the transcript into agent history.
 *
 * Only plain user text and agent prose carry forward — tone/rephrase cards are structured UI, and
 * replaying them as text would bloat the prompt without adding conversational meaning.
 */
private fun List<ChatItem>.toHistory(): List<AgentHistoryEntry> = mapNotNull { item ->
    when (item) {
        is ChatItem.UserDraft -> AgentHistoryEntry(isUser = true, text = item.text)
        is ChatItem.AgentReply -> AgentHistoryEntry(isUser = false, text = item.text)
        else -> null
    }
}

/** Swaps the placeholder typing bubble for the real response, keeping order stable. */
private fun List<ChatItem>.replaceThinking(
    thinkingId: Long,
    replacements: List<ChatItem>,
): List<ChatItem> = flatMap { item ->
    if (item is ChatItem.Thinking && item.id == thinkingId) replacements else listOf(item)
}
