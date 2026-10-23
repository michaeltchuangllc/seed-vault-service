package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.model.SEED_ADDRESS_INDEXES
import uniffi.algokit_crypto_ffi.Falcon1024Account
import uniffi.algokit_crypto_ffi.falcon1024AccountFromEntropy
import uniffi.algokit_crypto_ffi.falcon1024NthAddress
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

/**
 * Falcon-1024 seeds through the algokit-core Rust FFI (algokit-crypto and algokit-transact).
 *
 * A 25-word seed phrase encodes 32 bytes of entropy (the algo25 encoding). The entropy derives a
 * single Falcon-1024 key pair, which authorizes many accounts: address n uses the nth-lowest
 * PQ-compliant salt. Signed transactions carry the public key and that salt in their `pqsig`.
 *
 * Only top-level FFI functions are used, so the native libraries load on the first call, not when
 * this class loads.
 */
internal object Falcon1024 {
    /** Bytes of entropy behind a 25-word seed phrase. */
    const val ENTROPY_SIZE = 32

    fun randomEntropy(): ByteArray = randomBytes(ENTROPY_SIZE.toUInt())

    /** Decodes a 25-word seed phrase. Throws for an unknown word or a wrong checksum word. */
    fun entropyFromSeedPhrase(seedPhrase: List<String>): ByteArray = seedFromMnemonic(seedPhrase.joinToString(" "))

    fun seedPhraseFromEntropy(entropy: ByteArray): List<String> = mnemonicFromSeed(entropy).split(" ")

    /** The key pair behind [entropy]. Wipe its private key when done. */
    fun accountFromEntropy(entropy: ByteArray): Falcon1024Account = falcon1024AccountFromEntropy(entropy)

    /**
     * An account at every index in [SEED_ADDRESS_INDEXES] for the key pair of [publicKey], each
     * keeping [encryptedPrivateKey] (empty when the private key is not stored here).
     */
    fun accounts(
        publicKey: ByteArray,
        encryptedPrivateKey: ByteArray,
    ): List<StoredAccount> =
        SEED_ADDRESS_INDEXES.map { index ->
            val address = falcon1024NthAddress(publicKey, index.toUInt())
            StoredAccount(
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
