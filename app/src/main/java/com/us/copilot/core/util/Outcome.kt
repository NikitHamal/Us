package com.us.copilot.core.util

/** A tiny result type so the domain layer never throws across boundaries. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val valueOrNull: T? get() = (this as? Success)?.value
    val isSuccess: Boolean get() = this is Success
}

/** User-facing error taxonomy. Messages are resolved in the UI layer. */
sealed class AppError(val debugMessage: String) {
    data object NoNetwork : AppError("No network connection available.")
    data object CloudDisabled : AppError("Cloud AI is switched off in settings.")
    data object MissingCredentials : AppError("Base URL, API key or model name is not set.")
    data class Http(val code: Int, val body: String) : AppError("HTTP $code: $body")
    data class Parse(val reason: String) : AppError("Could not read the model response: $reason")
    data class Storage(val reason: String) : AppError("Storage problem: $reason")
    data class Unknown(val reason: String) : AppError(reason)
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)

fun AppError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)

inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    if (t is kotlin.coroutines.cancellation.CancellationException) throw t
    Outcome.Failure(AppError.Unknown(t.message ?: t::class.java.simpleName))
}
