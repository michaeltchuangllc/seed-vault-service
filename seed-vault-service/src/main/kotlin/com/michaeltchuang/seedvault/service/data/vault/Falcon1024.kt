package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.model.HUMAN_SIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.vault.StoredAccount
import org.bouncycastle.crypto.digests.SHA512tDigest
import uniffi.algokit_crypto_ffi.Falcon1024Account
import uniffi.algokit_crypto_ffi.falcon1024AccountFromEntropy
import uniffi.algokit_crypto_ffi.falcon1024CanonicalAddress
import uniffi.algokit_crypto_ffi.falcon1024RawSign
import uniffi.algokit_crypto_ffi.mnemonicFromSeed
import uniffi.algokit_crypto_ffi.randomBytes
import uniffi.algokit_crypto_ffi.seedFromMnemonic
import uniffi.algokit_transact_ffi.PqSignature
import uniffi.algokit_transact_ffi.SignedTransaction
import uniffi.algokit_transact_ffi.decodeTransaction
import uniffi.algokit_transact_ffi.encodeSignedTransaction
import uniffi.algokit_transact_ffi.encodeTransaction
import uniffi.algokit_transact_ffi.pqSchemeFalcon1024
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Falcon-1024 seeds through the algokit-core Rust FFI (algokit-crypto and algokit-transact).
 *
 * A 25-word seed phrase encodes 32 bytes of entropy (the algo25 encoding). The entropy derives a
 * Falcon-1024 key pair of address index 0. The other address indexes each have their own key pair
 * (see [accountAt]). Signed transactions carry the public key and the salt of the address in their `pqsig`.
 *
 * Only top-level FFI functions are used, so the native libraries load on the first call, not when
 * this class loads.
 */
internal object Falcon1024 {
    /** Largest address index, which the Trusty applet carries as one byte. */
    const val MAX_ADDRESS_INDEX = 255

    private val AUTOSIGN_ENTROPY_DOMAIN = "SeedVaultAutosign".encodeToByteArray()

    /** Bytes of entropy behind a 25-word seed phrase. */
    const val ENTROPY_SIZE = 32

    fun randomEntropy(): ByteArray = randomBytes(ENTROPY_SIZE.toUInt())

    /** Decodes a 25-word seed phrase. Throws for an unknown word or a wrong checksum word. */
    fun entropyFromSeedPhrase(seedPhrase: List<String>): ByteArray = seedFromMnemonic(seedPhrase.joinToString(" "))

    fun seedPhraseFromEntropy(entropy: ByteArray): List<String> = mnemonicFromSeed(entropy).split(" ")

    /** The key pair behind [entropy]. Wipe its private key when done. */
    fun accountFromEntropy(entropy: ByteArray): Falcon1024Account = falcon1024AccountFromEntropy(entropy)

    /**
     * The key pair of the account at [addressIndex]. Index 0 is the key behind [entropy] itself, the
     * one other wallets restore from the 25 words. Every other index (1 to [MAX_ADDRESS_INDEX]) is its
     * own key pair, from the child entropy of [autosignEntropy], so the 25 words recover it but it
     * reveals nothing about the other keys. The same derivation as the Trusty applet. Wipe the private
     * key when done.
     */
    fun accountAt(
        entropy: ByteArray,
        addressIndex: Int,
    ): Falcon1024Account {
        if (addressIndex == HUMAN_SIGN_ADDRESS_INDEX) return accountFromEntropy(entropy)
        val child = autosignEntropy(entropy, addressIndex)
        try {
            return accountFromEntropy(child)
        } finally {
            child.fill(0)
        }
    }

    /**
     * The entropy of the autosign key at [addressIndex]: SHA512/256("SeedVaultAutosign" || index as
     * 4 bytes little endian || entropy). Same as `falcon_key_entropy` in the algokit-core seed vault,
     * so Trusty and non-Trusty devices make the same autosign addresses from the same 25 words.
     */
    fun autosignEntropy(
        entropy: ByteArray,
        addressIndex: Int,
    ): ByteArray {
        require(addressIndex in 1..MAX_ADDRESS_INDEX) { "No autosign key at address index $addressIndex" }
        val input =
            AUTOSIGN_ENTROPY_DOMAIN +
                ByteBuffer
                    .allocate(Int.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(addressIndex)
                    .array() +
                entropy
        try {
            // BouncyCastle directly, since the platform providers do not all have SHA-512/256
            val digest = SHA512tDigest(ENTROPY_SIZE * Byte.SIZE_BITS)
            digest.update(input, 0, input.size)
            return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
        } finally {
            input.fill(0)
        }
    }

    /**
     * The account at [index] for its key pair's [publicKey]: the address with the key's canonical
     * (lowest PQ-compliant) salt, keeping [encryptedPrivateKey] (empty when the private key is not
     * stored here).
     */
    fun account(
        index: Int,
        publicKey: ByteArray,
        encryptedPrivateKey: ByteArray,
    ): StoredAccount {
        val address = falcon1024CanonicalAddress(publicKey)
        return StoredAccount(
            index = index,
            address = address.addressString,
            salt = address.salt.toInt(),
            publicKey = publicKey,
            encryptedPrivateKey = encryptedPrivateKey,
        )
    }

    /**
     * Signs arbitrary [data]. Data that starts with a prefix Algorand uses for something that moves
     * funds or delegates the account (a transaction, a group, a program) is refused, so it cannot be
     * signed past the transaction confirm screen. Same rule as the Trusty applet.
     */
    fun signBytes(
        privateKey: ByteArray,
        data: ByteArray,
    ): ByteArray {
        RESERVED_PREFIXES.firstOrNull { data.startsWith(it) }?.let { prefix ->
            throw IllegalArgumentException("The data starts with the reserved prefix \"${prefix.decodeToString()}\"")
        }
        return falcon1024RawSign(privateKey, data)
    }

    /**
     * Signs an unsigned msgpack [transaction] (without the "TX" prefix) as the address with [salt]
     * and returns the encoded signed transaction.
     */
    fun signTransaction(
        publicKey: ByteArray,
        privateKey: ByteArray,
        salt: Int,
        transaction: ByteArray,
    ): ByteArray {
        val decoded = decodeTransaction(transaction)
        // "TX" || msgpack(txn), the same bytes an Ed25519 signer signs
        val signature = falcon1024RawSign(privateKey, encodeTransaction(decoded))
        return encodeSignedTransaction(
            SignedTransaction(
                transaction = decoded,
                signature = null,
                authAddress = null,
                multisignature = null,
                logicSignature = null,
                pqSignature =
                    PqSignature(
                        publicKey = publicKey,
                        scheme = pqSchemeFalcon1024(),
                        signature = signature,
                        salt = salt.toUByte(),
                    ),
            ),
        )
    }

    private val RESERVED_PREFIXES = listOf("TX", "TG", "Program", "ProgData").map { it.encodeToByteArray() }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
