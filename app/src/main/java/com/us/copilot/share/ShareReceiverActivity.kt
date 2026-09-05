package com.us.copilot.share

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.us.copilot.ui.theme.UsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives ACTION_SEND / PROCESS_TEXT from Instagram, Messenger or any other app.
 *
 * This is the *only* automatic ingestion path apart from the opt-in notification listener, and it
 * always requires a deliberate user action in the other app. Us never reads a conversation.
 */
@AndroidEntryPoint
class ShareReceiverActivity : FragmentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                val text = extractText(intent)
                viewModel.accept(text, referrerPackage())
            }

            LaunchedEffect(state.saved) { if (state.saved) finish() }

            UsTheme {
                ShareSheet(
                    state = state,
                    onSpeakerChange = viewModel::setSpeaker,
                    onEmotionChange = viewModel::setEmotion,
                    onUnresolvedChange = viewModel::setUnresolved,
                    onRephrase = viewModel::rephrase,
                    onSave = viewModel::save,
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.accept(extractText(intent), referrerPackage())
    }

    private fun extractText(intent: Intent?): String = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        else -> ""
    }

    private fun referrerPackage(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        referrer?.host
    } else {
        null
    }
}
