package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.vault.SecretCipher

/**
 * A [SecretCipher] that tags what it encrypts with its [name], so a test can tell which key was
 * used, and refuses to work while [locked], like a Keystore key that needs a fresh verification.
 */
internal class FakeSecretCipher(
    private val name: String,
) : SecretCipher {
    var locked = false

    override fun encrypt(plain: ByteArray): ByteArray {
        check(!locked) { "The user has not verified themselves" }
        return "$name:".encodeToByteArray() + plain
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        check(!locked) { "The user has not verified themselves" }
        val prefix = "$name:".encodeToByteArray()
        // A different key cannot read it, like AES-GCM failing its tag check
        require(encrypted.size >= prefix.size && prefix.indices.all { encrypted[it] == prefix[it] }) { "Wrong key" }
        return encrypted.copyOfRange(prefix.size, encrypted.size)
    }

    companion object {
        fun tagOf(encrypted: ByteArray) = encrypted.decodeToString().substringBefore(':')
    }
}
