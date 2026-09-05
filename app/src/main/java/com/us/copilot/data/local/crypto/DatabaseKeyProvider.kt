package com.us.copilot.data.local.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and stores the SQLCipher passphrase.
 *
 * A 256-bit random value is created once on first launch and kept in [SecureStore], which is itself
 * encrypted with a hardware-backed Keystore key. The user never sees or types it, and it never
 * leaves the device.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    private val secureStore: SecureStore,
) {

    fun passphrase(): CharArray {
        val existing = secureStore.getString(SecureStore.KEY_DB_PASSPHRASE)
        if (existing.isNotBlank()) return existing.toCharArray()

        val bytes = ByteArray(KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        bytes.fill(0)
        secureStore.putString(SecureStore.KEY_DB_PASSPHRASE, encoded)
        return encoded.toCharArray()
    }

    fun passphraseBytes(): ByteArray = String(passphrase()).toByteArray(Charsets.UTF_8)

    /** Used by "delete everything": without the key the database file is unreadable noise. */
    fun destroy() = secureStore.remove(SecureStore.KEY_DB_PASSPHRASE)

    private companion object { const val KEY_SIZE_BYTES = 32 }
}
