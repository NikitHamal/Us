package com.us.copilot.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.us.copilot.R
import com.us.copilot.core.util.AppError

/** Maps the domain error taxonomy onto user-facing strings. */
@Composable
fun messageFor(error: AppError): String = when (error) {
    AppError.NoNetwork -> stringResource(R.string.error_no_network)
    AppError.CloudDisabled -> stringResource(R.string.error_cloud_disabled)
    AppError.MissingCredentials -> stringResource(R.string.error_missing_credentials)
    is AppError.Http -> stringResource(R.string.error_http, error.code)
    is AppError.Parse -> stringResource(R.string.error_parse)
    is AppError.Storage -> stringResource(R.string.error_storage)
    is AppError.Unknown -> stringResource(R.string.error_unknown_detail, error.debugMessage)
}
