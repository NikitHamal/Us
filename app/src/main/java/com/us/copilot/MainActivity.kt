package com.us.copilot

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.us.copilot.ui.UsApp
import com.us.copilot.ui.MainViewModel
import com.us.copilot.ui.theme.UsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host. FragmentActivity is required by BiometricPrompt; the whole UI is Compose.
 * Edge-to-edge and predictive back are enabled here and in the manifest.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        splash.setKeepOnScreenCondition { !viewModel.isReady.value }
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsState()
            UsTheme(themeMode = state.themeMode, dynamicColor = state.dynamicColor) {
                UsApp(
                    state = state,
                    onUnlockRequested = { viewModel.unlock(this) },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }
}
