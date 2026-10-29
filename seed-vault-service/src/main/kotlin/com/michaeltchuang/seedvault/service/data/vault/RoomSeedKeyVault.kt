package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.model.AUTOSIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.model.SEED_ADDRESS_INDEXES
import com.michaeltchuang.seedvault.service.domain.vault.FalconKeys
import com.michaeltchuang.seedvault.service.domain.vault.SecretCipher
import com.michaeltchuang.seedvault.service.domain.vault.SeedKeyVault
import com.michaeltchuang.seedvault.service.domain.vault.SeedStorage
import com.michaeltchuang.seedvault.service.domain.vault.StoredAccount
import com.michaeltchuang.seedvault.service.domain.vault.StoredSeed

/**
 * Keeps the entropy behind each seed phrase (in the seed row) and the Falcon-1024 private key of
 * each account (in its account row) encrypted with StrongBox / TEE Keystore keys. Every address index
 * has its own Falcon key pair. [cipher] needs a fresh user verification, so revealing a seed and
 * signing as the human sign account fail unless the user just verified themselves. [autosignCipher]
 * does not, and only protects the autosign account's key, which cannot sign for the human sign
 * account. Used when the device has no Trusty applet. The keys are derived and used with the
 * algokit-core Rust FFI ([NativeFalconKeys]).
 */
internal class RoomSeedKeyVault(
    private val cipher: SecretCipher,
    private val autosignCipher: SecretCipher,
    private val falcon: FalconKeys = NativeFalconKeys,
) : SeedKeyVault {
    override val storage = SeedStorage.ROOM

    override suspend fun isAvailable() = true

    override suspend fun addSeed(seedPhrase: List<String>): StoredSeed {
        val entropy = falcon.entropyFromSeedPhrase(seedPhrase)
        try {
            return StoredSeed(
                encryptedEntropy = cipher.encrypt(entropy),
                accounts =
                    SEED_ADDRESS_INDEXES.map { index ->
                        val keyPair = falcon.keyPairAt(entropy, index)
                        try {
                            falcon.account(index, keyPair.publicKey).withPrivateKey(cipherFor(index).encrypt(keyPair.privateKey))
                        } finally {
                            keyPair.privateKey.fill(0)
                        }
                    },
            )
        } finally {
            entropy.fill(0)
        }
    }

    override suspend fun revealSeedPhrase(seed: StoredSeed): List<String> {
        val entropy = cipher.decrypt(seed.encryptedEntropy)
        try {
            return falcon.seedPhraseFromEntropy(entropy)
        } finally {
            entropy.fill(0)
        }
    }

    override suspend fun signBytes(
        seed: StoredSeed,
        addressIndex: Int,
        data: ByteArray,
    ): ByteArray? = withPrivateKey(seed.account(addressIndex)) { privateKey -> falcon.signBytes(privateKey, data) }

    override suspend fun signTransaction(
        seed: StoredSeed,
        addressIndex: Int,
        transaction: ByteArray,
    ): ByteArray? {
        val account = seed.account(addressIndex)
        return withPrivateKey(account) { privateKey ->
            falcon.signTransaction(account.publicKey, privateKey, account.salt, transaction)
        }
    }

    /** The secrets only exist in the seed and account rows, which the repository deletes. */
    override suspend fun deleteSeed(seed: StoredSeed) = Unit

    private suspend fun <T> withPrivateKey(
        account: StoredAccount,
        block: (ByteArray) -> T,
    ): T {
        val privateKey = cipherFor(account.index).decrypt(account.encryptedPrivateKey)
        try {
            return block(privateKey)
        } finally {
            privateKey.fill(0)
        }
    }

    private fun StoredAccount.withPrivateKey(encryptedPrivateKey: ByteArray) =
        StoredAccount(index, address, salt, publicKey, encryptedPrivateKey)

    private fun cipherFor(addressIndex: Int) = if (addressIndex == AUTOSIGN_ADDRESS_INDEX) autosignCipher else cipher
}
