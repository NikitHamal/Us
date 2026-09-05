package com.us.copilot.core.model

/**
 * A notification captured from a watched app.
 *
 * This is a record of something that arrived on the device — nothing more. It is inert until the
 * user does something with it. In particular [sharedWithAi] is the only thing that ever puts this
 * text in front of a model, and it is always set by an explicit user action.
 */
data class CapturedNotification(
    val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val fingerprint: String,
    val sharedWithAi: Boolean = false,
    val riskLevel: String? = null,
) {
    /** Single-line preview for the history list. */
    val preview: String
        get() = text.replace('\n', ' ').trim().take(PREVIEW_CHARS)

    companion object {
        const val PREVIEW_CHARS = 140

        /** Hard cap on stored history so capture cannot grow without bound. */
        const val RETENTION_LIMIT = 500
    }
}

/** An app the user can choose to watch, as shown in the picker. */
data class WatchableApp(
    val packageName: String,
    val label: String,
    val isWatched: Boolean,
)
