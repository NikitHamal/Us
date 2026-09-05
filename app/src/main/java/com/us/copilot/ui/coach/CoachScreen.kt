package com.us.copilot.ui.coach

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.us.copilot.R
import com.us.copilot.core.model.MemorySource
import com.us.copilot.core.model.Speaker
import com.us.copilot.ui.theme.UsDimens
import com.us.copilot.ui.util.messageFor

/**
 * The coach, as a conversation.
 *
 * Layout is deliberately three layers: a transparent-ish top bar, a scrolling transcript that
 * runs full-bleed behind the composer, and the floating composer pinned above the keyboard.
 * Nothing is boxed in panels — the content is the interface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    prefillText: String? = null,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudEnabled by viewModel.cloudEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val savedLabel = stringResource(R.string.share_saved)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.addAttachments(uris)
    }

    LaunchedEffect(prefillText) {
        if (!prefillText.isNullOrBlank()) viewModel.prefill(prefillText)
    }

    LaunchedEffect(state.savedMessage) {
        if (state.savedMessage) {
            snackbarHostState.showSnackbar(savedLabel)
            viewModel.consumeSavedMessage()
        }
    }

    LaunchedEffect(state.attachError) {
        state.attachError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeAttachError()
        }
    }

    // Keep the newest turn in view as the transcript grows.
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.coach_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    AnimatedVisibility(visible = state.canSaveMoment, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(
                            onClick = { viewModel.saveAsMemory(Speaker.ME, MemorySource.MANUAL) },
                        ) {
                            Icon(
                                Icons.Filled.BookmarkAdd,
                                contentDescription = stringResource(R.string.coach_save_memory),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            if (state.isEmpty) {
                CoachWelcome(
                    onPick = viewModel::applyStarter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = UsDimens.screenPadding),
                )
            } else {
                Transcript(
                    state = state,
                    listState = listState,
                    onUseRewrite = viewModel::useRewrite,
                    onRetry = viewModel::retry,
                    bottomInset = 56.dp,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModelBar(
                    modelLabel = state.modelLabel,
                    cloudEnabled = cloudEnabled,
                    onOpenSheet = { viewModel.setModelSheetVisible(true) },
                )
                AttachmentChips(
                    attachments = state.attachments,
                    onRemove = { viewModel.removeAttachment(it.uri) },
                )
                ChatComposer(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    onSend = viewModel::ask,
                    onCheckDraft = viewModel::analyze,
                    enabled = !state.isBusy,
                    canAttach = state.canAttach && cloudEnabled,
                    onAttach = { filePicker.launch("*/*") },
                )
            }
        }
    }

    if (state.showModelSheet) {
        ModelSheet(
            config = state.nebians,
            cloudEnabled = cloudEnabled,
            onSetCloudAi = viewModel::setCloudAi,
            onDismiss = { viewModel.setModelSheetVisible(false) },
            onSelectProvider = viewModel::selectProvider,
            onSelectModel = viewModel::selectModel,
            onSelectEffort = viewModel::selectEffort,
            onSelectTemperature = { viewModel.selectTemperature(it) },
            onSelectMaxTokens = { viewModel.selectMaxTokens(it) },
        )
    }
}

@Composable
private fun Transcript(
    state: CoachUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onUseRewrite: (String) -> Unit,
    onRetry: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UsDimens.screenPadding,
            end = UsDimens.screenPadding,
            top = 12.dp,
            // Leaves room for the floating composer so the last bubble is never hidden.
            bottom = bottomInset + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(
            count = state.items.size,
            key = { index -> state.items[index].id },
        ) { index ->
            when (val item = state.items[index]) {
                is ChatItem.UserDraft -> UserBubble(item.text)
                is ChatItem.CoachSays -> CoachBubble(stringResource(item.textRes))
                is ChatItem.Thinking -> ThinkingBubble()
                is ChatItem.ToneCard -> CoachAttachment { ToneResultCard(item.tone) }
                is ChatItem.RephraseCardItem -> CoachAttachment {
                    RephraseCard(rephrase = item.rephrase, onUseRewrite = onUseRewrite)
                }
                is ChatItem.AgentReply -> AgentReplyBubble(
                    text = item.text,
                    steps = item.steps,
                    hitCap = item.hitIterationCap,
                )
                is ChatItem.ErrorBubble -> CoachAttachment {
                    ErrorBubbleCard(message = messageFor(item.error), onRetry = onRetry)
                }
            }
        }
    }
}

/** First-run state: a warm greeting plus starter chips, never a blank page. */
@Composable
private fun CoachWelcome(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { CoachAvatar(Modifier.size(64.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.coach_greeting),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.coach_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        StarterChips(onPick = onPick)
        Spacer(Modifier.height(96.dp))
    }
}
