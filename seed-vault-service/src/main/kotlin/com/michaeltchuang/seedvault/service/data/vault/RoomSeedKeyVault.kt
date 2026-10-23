package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.walletsdk.core.encryption.decryptByteArray
import com.michaeltchuang.walletsdk.core.encryption.encryptByteArray
import com.michaeltchuang.walletsdk.core.encryption.initializeEncryptionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the entropy behind each seed phrase (in the seed row) and the Falcon-1024 private key of
 * each account (in its account row) encrypted with the AES-GCM Android Keystore key from
 * wallet-sdk-core. Used when the device has no Trusty applet. The keys are derived and used with the
 * algokit-core Rust FFI ([Falcon1024]).
 */
internal class RoomSeedKeyVault : SeedKeyVault {
    override val storage = SeedStorage.ROOM

    private val keyMutex = Mutex()
    private var isKeyReady = false

    override suspend fun isAvailable() = true

    override suspend fun addSeed(seedPhrase: List<String>): StoredSeed {
        ensureEncryptionKey()
        val entropy = Falcon1024.entropyFromSeedPhrase(seedPhrase)
        try {
            val account = Falcon1024.accountFromEntropy(entropy)
            try {
                return StoredSeed(
                    encryptedEntropy = encryptByteArray(entropy),
                    accounts = Falcon1024.accounts(account.publicKey, encryptByteArray(account.privateKey)),
                )
            } finally {
                account.privateKey.fill(0)
            }
        } finally {
            entropy.fill(0)
        }
    }

    override suspend fun revealSeedPhrase(seed: StoredSeed): List<String> {
        ensureEncryptionKey()
        val entropy = decryptByteArray(seed.encryptedEntropy)
        try {
            return Falcon1024.seedPhraseFromEntropy(entropy)
        } finally {
            entropy.fill(0)
        }
    }

    override suspend fun signBytes(
        seed: StoredSeed,
        addressIndex: Int,
        data: ByteArray,
    ): ByteArray? = withPrivateKey(seed.account(addressIndex)) { privateKey -> Falcon1024.signBytes(privateKey, data) }

    override suspend fun signTransaction(
        seed: StoredSeed,
        addressIndex: Int,
        transaction: ByteArray,
    ): ByteArray? {
        val account = seed.account(addressIndex)
        return withPrivateKey(account) { privateKey ->
            Falcon1024.signTransaction(account.publicKey, privateKey, account.salt, transaction)
        }
    }

    /** The secrets only exist in the seed and account rows, which the repository deletes. */
    override suspend fun deleteSeed(seed: StoredSeed) = Unit

    private suspend fun <T> withPrivateKey(
        account: StoredAccount,
        block: (ByteArray) -> T,
    ): T {
        ensureEncryptionKey()
        val privateKey = decryptByteArray(account.encryptedPrivateKey)
        try {
            return block(privateKey)
        } finally {
            privateKey.fill(0)
        }
    }

    /**
     * Creates the Keystore key before the first encrypt / decrypt. wallet-sdk-core also does this
     * from a background coroutine when its AES manager is created, which could race the first write.
     */
    private suspend fun ensureEncryptionKey() {
        keyMutex.withLock {
            if (!isKeyReady) {
                initializeEncryptionManager()
                isKeyReady = true
            }
        }
    }
}
