package com.us.copilot.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Wraps BiometricPrompt so the UI layer only sees a suspending result. */
object BiometricGate {

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Why authentication is not currently possible, so the UI can say something useful. */
    enum class Availability { AVAILABLE, NO_HARDWARE, NOT_ENROLLED, UNAVAILABLE }

    fun availability(activity: FragmentActivity): Availability =
        when (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNAVAILABLE
        }

    fun isAvailable(activity: FragmentActivity): Boolean =
        availability(activity) == Availability.AVAILABLE

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): Result = suspendCancellableCoroutine { continuation ->
        // Previously this resumed with `true` when no authenticator was available, which meant a
        // device with no enrolled credential silently unlocked an app the user had asked to lock.
        // Report it instead and let the caller decide what to show.
        val availability = availability(activity)
        if (availability != Availability.AVAILABLE) {
            continuation.resume(Result.Unavailable(availability))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(Result.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    continuation.resume(
                        if (cancelled) Result.Cancelled else Result.Error(errString.toString()),
                    )
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Error(val message: String) : Result
        data class Unavailable(val reason: Availability) : Result
    }
}
