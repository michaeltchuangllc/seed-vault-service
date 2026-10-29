package com.michaeltchuang.seedvault.service.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import com.michaeltchuang.seedvault.service.domain.vault.SecretCipher
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that never leaves the Android Keystore. The key lives in the StrongBox
 * secure element when the device has one, otherwise in the TEE. With [requiresUserAuth] the hardware
 * only lets it be used within [AUTH_VALIDITY_SECONDS] of the user verifying themselves with a strong
 * biometric or the screen lock, so what it protects is out of reach of this app's own code until the
 * user authenticated (see [com.michaeltchuang.seedvault.service.data.auth.OsUserAuthenticator]).
 * Without it the key is only bound to the hardware and this app, which autosign needs.
 *
 * [encrypt] and [decrypt] throw [android.security.keystore.UserNotAuthenticatedException] when the
 * user has not verified recently, and [android.security.keystore.KeyPermanentlyInvalidatedException]
 * when the screen lock was removed and Android deleted the key. Both mean the secret cannot be used.
 * The output is the 12 byte IV followed by the ciphertext and tag.
 */
internal class KeystoreSecretCipher(
    private val alias: String,
    private val requiresUserAuth: Boolean,
) : SecretCipher {
    private val keyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > IV_SIZE) { "Not an encrypted secret" }
        val buffer = ByteBuffer.wrap(encrypted)
        val iv = ByteArray(IV_SIZE).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE)
    }

    @Synchronized
    private fun key(): SecretKey = (keyStore.getKey(alias, null) as? SecretKey) ?: generateKey()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val key =
            try {
                generator.init(spec(strongBox = true))
                generator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                generator.init(spec(strongBox = false))
                generator.generateKey()
            }
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        Log.i(TAG, "Created the seed encryption key, security level ${info.securityLevel}")
        return key
    }

    private fun spec(strongBox: Boolean) =
        KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply {
                if (requiresUserAuth) {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    // Enrolling another fingerprint keeps the key, the screen lock is accepted as a proof anyway
                    setInvalidatedByBiometricEnrollment(false)
                }
            }.setIsStrongBoxBacked(strongBox)
            .build()

    companion object {
        /**
         * How long a verification unlocks the key. At least as long as an approval from the confirm
         * sign screen stays valid, so the client's sign call that follows it still works.
         */
        const val AUTH_VALIDITY_SECONDS = 60

        private const val TAG = "KeystoreSecretCipher"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Key alias for the secrets that need the user: the entropy and the human sign account's private key. */
        const val USER_AUTH_KEY_ALIAS = "seed_vault_user_auth_key"

        /** Key alias for the autosign account's private key, which can be used without the user. */
        const val AUTOSIGN_KEY_ALIAS = "seed_vault_autosign_key"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
