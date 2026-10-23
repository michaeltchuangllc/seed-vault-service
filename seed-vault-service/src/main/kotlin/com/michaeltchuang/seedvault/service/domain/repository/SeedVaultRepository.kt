package com.michaeltchuang.seedvault.service.domain.repository

import com.michaeltchuang.seedvault.service.domain.model.VaultSeed
import kotlinx.coroutines.flow.Flow

/**
 * Storage for the seeds in the vault. Seed phrases go in and come out as plain words; how they
 * are encoded and encrypted at rest is up to the implementation.
 */
interface SeedVaultRepository {
    fun getSeeds(): Flow<List<VaultSeed>>

    fun getSeed(seedId: Long): Flow<VaultSeed?>

    /** Stores the seed phrase with an address for every index of [com.michaeltchuang.seedvault.service.domain.model.SEED_ADDRESS_INDEXES]. */
    suspend fun addSeed(
        seedPhrase: List<String>,
        isBackedUp: Boolean,
    ): VaultSeed

    /** Returns the decrypted seed phrase, or null if the seed no longer exists. */
    suspend fun getSeedPhrase(seedId: Long): List<String>?

    /**
     * Signs [data] with the Falcon-1024 key behind [address], one of a seed's accounts. Returns
     * null if no seed has that address or signing fails.
     */
    suspend fun signBytes(
        address: String,
        data: ByteArray,
    ): ByteArray?

    /**
     * Signs an unsigned Algorand [transaction] as [address], one of a seed's accounts, and returns
     * the signed transaction. Returns null if no seed has that address or signing fails.
     */
    suspend fun signTransaction(
        address: String,
        transaction: ByteArray,
    ): ByteArray?

    /** Returns the Falcon-1024 public key of the account with [address], or null if no seed has it. */
    suspend fun getPublicKey(address: String): ByteArray?

    /** Lets the app with [packageName] request signatures from the seed. */
    suspend fun authorizeApp(
        seedId: Long,
        packageName: String,
        label: String,
    )

    suspend fun renameSeed(
        seedId: Long,
        name: String,
    )

    suspend fun deleteSeed(seedId: Long)

    suspend fun revokeApp(
        seedId: Long,
        packageName: String,
    )
}
