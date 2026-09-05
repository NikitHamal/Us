package com.us.copilot.ai.offline

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the optional on-device model bundle (`assets/models/needle2.cact`).
 *
 * The app ships fully functional without it: [state] then reports [ModelState.RulesOnly] and the
 * offline provider answers from its rule engines with a lower confidence score. Dropping the real
 * `.cact` file into `app/src/main/assets/models/` upgrades inference with no code change.
 *
 * The loader also owns the tool-calling surface a real model would use, so the wiring is already
 * in place: see [ToolRegistry].
 */
@Singleton
class CactModelLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolRegistry: ToolRegistry,
) {

    sealed interface ModelState {
        /** No model bundle present. Deterministic rules answer everything. */
        data object RulesOnly : ModelState
        /** Bundle extracted and verified; runtime is ready to serve inference. */
        data class Ready(val file: File, val sizeBytes: Long, val tools: List<String>) : ModelState
        data class Failed(val reason: String) : ModelState
    }

    private val mutex = Mutex()
    @Volatile private var cached: ModelState? = null

    suspend fun state(): ModelState = cached ?: mutex.withLock {
        cached ?: load().also { cached = it }
    }

    suspend fun isReady(): Boolean = state() is ModelState.Ready

    /** Extra confidence granted when a real model bundle is available. */
    suspend fun confidenceBonus(): Float = if (isReady()) 0.25f else 0f

    private suspend fun load(): ModelState = withContext(Dispatchers.IO) {
        runCatching {
            val assets = context.assets.list(ASSET_DIR)?.toList().orEmpty()
            if (!assets.contains(MODEL_FILE)) return@withContext ModelState.RulesOnly

            val target = File(context.filesDir, "models/$MODEL_FILE")
            target.parentFile?.mkdirs()

            val assetSize = context.assets.open("$ASSET_DIR/$MODEL_FILE").use { input ->
                if (target.exists()) {
                    input.available().toLong()
                } else {
                    target.outputStream().use { output -> input.copyTo(output) }
                    target.length()
                }
            }
            if (target.length() < MIN_VALID_BYTES) {
                target.delete()
                return@withContext ModelState.Failed("Model bundle is truncated ($assetSize bytes).")
            }
            ModelState.Ready(
                file = target,
                sizeBytes = target.length(),
                tools = toolRegistry.toolNames(),
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "Model load failed", throwable)
            ModelState.Failed(throwable.message ?: "Unknown model loading error")
        }
    }

    private companion object {
        const val TAG = "CactModelLoader"
        const val ASSET_DIR = "models"
        const val MODEL_FILE = "needle2.cact"
        const val MIN_VALID_BYTES = 1024L
    }
}
