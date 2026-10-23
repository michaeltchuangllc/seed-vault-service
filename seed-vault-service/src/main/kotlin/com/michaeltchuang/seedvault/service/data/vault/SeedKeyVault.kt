package com.michaeltchuang.seedvault.service.data.vault

/** Where the secret keys of a seed live. The name is stored in the seed row. */
internal enum class SeedStorage {
    /** Encrypted with the Android Keystore key and kept in the Room database. */
    ROOM,

    /** Inside the Trusty applet in the TEE, which never hands out the keys. */
    TRUSTY,
}

/**
 * One account of a seed: its [index], its [address], the PQ address [salt] that makes the address,
 * and its key pair. [encryptedPrivateKey] is only used by [SeedStorage.ROOM]; for
 * [SeedStorage.TRUSTY] it is empty, since the private key stays in the TEE.
 */
internal class StoredAccount(
    val index: Int,
    val address: String,
    val salt: Int,
    val publicKey: ByteArray,
    val encryptedPrivateKey: ByteArray,
)

/**
 * What the database holds for a seed. [encryptedEntropy] is only used by [SeedStorage.ROOM]; for
 * [SeedStorage.TRUSTY] it is empty, since the secrets stay in the TEE.
 *
 * @param accounts one per address index
 */
internal class StoredSeed(
    val encryptedEntropy: ByteArray,
    val accounts: List<StoredAccount>,
) {
    fun account(index: Int): StoredAccount =
        accounts.firstOrNull { it.index == index } ?: throw IllegalArgumentException("The seed has no account at index $index")
}

/**
 * Keeps the secrets of Falcon-1024 seeds and does everything that needs them. The repository picks
 * an implementation per seed, so the rest of the service does not care where the keys are.
 */
internal interface SeedKeyVault {
    val storage: SeedStorage

    /** False when this vault cannot be used on the device, e.g. there is no Trusty applet. */
    suspend fun isAvailable(): Boolean

    /** Stores a 25-word seed phrase and returns what the database needs to keep, with every account. */
    suspend fun addSeed(seedPhrase: List<String>): StoredSeed

    suspend fun revealSeedPhrase(seed: StoredSeed): List<String>

    /** Signs raw bytes as the address at [addressIndex]. Throws [IllegalArgumentException] if the vault refuses the data. */
    suspend fun signBytes(
        seed: StoredSeed,
        addressIndex: Int,
        data: ByteArray,
    ): ByteArray?

    /** Signs an unsigned Algorand [transaction] as the address at [addressIndex] and returns the signed transaction. */
    suspend fun signTransaction(
        seed: StoredSeed,
        addressIndex: Int,
        transaction: ByteArray,
    ): ByteArray?

    /** Removes the secrets of the seed. */
    suspend fun deleteSeed(seed: StoredSeed)
}
