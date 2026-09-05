package com.us.copilot.ui.coach

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.us.copilot.R
import com.us.copilot.ai.agent.AgentAttachment
import com.us.copilot.ai.model.ToneAnalysis
import com.us.copilot.ai.nebians.NebiansCatalog
import com.us.copilot.core.model.Emotion
import com.us.copilot.core.model.Memory
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.core.util.Outcome
import com.us.copilot.ai.agent.AgentHistoryEntry
import com.us.copilot.domain.repository.NebiansConfig
import com.us.copilot.domain.repository.NebiansEffort
import com.us.copilot.domain.repository.SettingsRepository
import com.us.copilot.domain.usecase.AskCoachUseCase
import com.us.copilot.domain.usecase.BeforeYouSendUseCase
import com.us.copilot.domain.usecase.RephraseUseCase
import com.us.copilot.domain.usecase.SaveMemoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val nebians: NebiansConfig = NebiansConfig(),
    val attachments: List<AttachmentUi> = emptyList(),
    val showModelSheet: Boolean = false,
    val attachError: String? = null,
) {
    val hasInput: Boolean get() = draft.trim().length >= 2
    val isBusy: Boolean get() = isAnalyzing || isRephrasing || isAsking
    val isEmpty: Boolean get() = items.isEmpty()
    val canSaveMoment: Boolean get() = lastTone != null && lastAnalyzed.isNotBlank()

    /** Label for the model bar, e.g. "TryingOpen · Qwen3.8 27B". */
    val modelLabel: String
        get() {
            val provider = NebiansCatalog.find(nebians.providerSlug)
            val model = NebiansCatalog.modelFor(nebians.providerSlug, nebians.modelId)
            val providerLabel = provider?.label?.substringBefore(" (") ?: nebians.providerSlug
            val modelLabel = model?.label ?: nebians.modelId.ifBlank { provider?.defaultModel.orEmpty() }
            return if (modelLabel.isBlank()) providerLabel else "$providerLabel · $modelLabel"
        }

    /** True when the selected model can receive the staged files. */
    val canAttach: Boolean
        get() {
            val provider = NebiansCatalog.find(nebians.providerSlug) ?: return false
            if (!provider.supportsFiles) return false
            if (nebians.modelId.isBlank()) return true
            return NebiansCatalog.modelFor(nebians.providerSlug, nebians.modelId)?.fileUpload != false
        }
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val beforeYouSend: BeforeYouSendUseCase,
    private val askCoach: AskCoachUseCase,
    private val rephraseUseCase: RephraseUseCase,
    private val saveMemory: SaveMemoryUseCase,
    private val settings: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    /** Cloud toggle, so the model bar can explain why network models are off. */
    val cloudEnabled: StateFlow<Boolean> = settings.cloudEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            settings.nebiansConfig.collect { config ->
                _uiState.update { it.copy(nebians = config) }
            }
        }
    }

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
        val staged = _uiState.value.attachments
        if ((text.length < 2 && staged.isEmpty()) || _uiState.value.isBusy) return

        val thinkingId = id()
        val history = _uiState.value.items.toHistory()

        _uiState.update {
            it.copy(
                items = it.items +
                    ChatItem.UserDraft(id(), text.ifBlank { staged.joinToString(", ") { file -> file.name } }) +
                    ChatItem.Thinking(thinkingId),
                draft = "",
                attachments = emptyList(),
                isAsking = true,
            )
        }

        viewModelScope.launch {
            val files = readAttachments(staged)
            val replacement = when (val result = askCoach(text, history, files)) {
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
        _uiState.value = CoachUiState(nebians = _uiState.value.nebians)
    }

    // --- Model selection (Nebians fleet) ------------------------------------

    fun setModelSheetVisible(visible: Boolean) = _uiState.update { it.copy(showModelSheet = visible) }

    fun selectProvider(slug: String) {
        viewModelScope.launch { settings.setNebiansProvider(slug) }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch { settings.setNebiansModel(modelId) }
    }

    fun selectEffort(effort: NebiansEffort) {
        viewModelScope.launch { settings.setNebiansEffort(effort) }
    }

    fun selectTemperature(value: Float) {
        viewModelScope.launch { settings.setNebiansTemperature(value) }
    }

    fun selectMaxTokens(value: Int) {
        viewModelScope.launch { settings.setNebiansMaxTokens(value) }
    }

    fun consumeAttachError() = _uiState.update { it.copy(attachError = null) }

    // --- Attachments (only file-capable models receive them) ------------------

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value.attachments.toMutableList()
            var rejection: String? = null
            for (uri in uris) {
                if (current.size >= MAX_ATTACHMENTS) {
                    rejection = "At most $MAX_ATTACHMENTS files per message"
                    break
                }
                val meta = queryFileMeta(uri)
                if (meta == null) {
                    rejection = "Could not read that file"
                    continue
                }
                val (name, mime, size) = meta
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext.isBlank() || ext !in ALLOWED_ATTACHMENT_EXTENSIONS) {
                    rejection = "File type .$ext is not supported"
                    continue
                }
                if (size > MAX_ATTACHMENT_BYTES) {
                    rejection = "$name is larger than 5 MB"
                    continue
                }
                if (current.none { it.uri == uri }) {
                    current.add(AttachmentUi(uri = uri, name = name, mimeType = mime, sizeBytes = size))
                }
            }
            _uiState.update { it.copy(attachments = current.toList(), attachError = rejection) }
        }
    }

    fun removeAttachment(uri: Uri) {
        _uiState.update { state -> state.copy(attachments = state.attachments.filterNot { it.uri == uri }) }
    }

    private suspend fun readAttachments(staged: List<AttachmentUi>): List<AgentAttachment> =
        withContext(Dispatchers.IO) {
            staged.take(MAX_ATTACHMENTS).mapNotNull { file ->
                try {
                    val bytes = appContext.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                        ?: return@mapNotNull null
                    if (bytes.size > MAX_ATTACHMENT_BYTES || bytes.isEmpty()) return@mapNotNull null
                    AgentAttachment(
                        filename = file.name,
                        mimeType = file.mimeType.ifBlank { "application/octet-stream" },
                        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }

    private fun queryFileMeta(uri: Uri): Triple<String, String, Long>? {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: return null
        var size = 0L
        try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            return null
        }
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        return Triple(name, mime, size)
    }

    private fun intensityFrom(tone: ToneAnalysis?): Int = when {
        tone == null -> 3
        tone.harshnessScore >= 70 -> 5
        tone.harshnessScore >= 45 -> 4
        tone.harshnessScore >= 20 -> 3
        else -> 2
    }

    companion object {
        const val MAX_ATTACHMENTS = 3
        const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024L
        val ALLOWED_ATTACHMENT_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
            "pdf", "txt", "doc", "docx",
            "mp4", "mov", "webm",
            "mp3", "wav", "ogg", "flac", "aac", "m4a",
        )
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
