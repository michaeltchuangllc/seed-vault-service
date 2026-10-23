package com.michaeltchuang.seedvault.service.data.repository

import android.util.Log
import com.michaeltchuang.seedvault.service.data.database.AuthorizedAppEntity
import com.michaeltchuang.seedvault.service.data.database.SeedAccountEntity
import com.michaeltchuang.seedvault.service.data.database.SeedDao
import com.michaeltchuang.seedvault.service.data.database.SeedEntity
import com.michaeltchuang.seedvault.service.data.database.SeedWithApps
import com.michaeltchuang.seedvault.service.data.vault.SeedKeyVault
import com.michaeltchuang.seedvault.service.data.vault.SeedStorage
import com.michaeltchuang.seedvault.service.data.vault.StoredAccount
import com.michaeltchuang.seedvault.service.data.vault.StoredSeed
import com.michaeltchuang.seedvault.service.domain.model.AuthorizedApp
import com.michaeltchuang.seedvault.service.domain.model.VaultAddress
import com.michaeltchuang.seedvault.service.domain.model.VaultSeed
import com.michaeltchuang.seedvault.service.domain.repository.SeedVaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room backed [SeedVaultRepository]. Names, backup state, accounts, public keys and authorized
 * apps live in Room. The secret keys live in a [SeedKeyVault]: the Trusty applet in the TEE when the
 * device has one, otherwise encrypted in the database with the Android Keystore. Each seed
 * remembers which one holds its keys.
 */
internal class SeedVaultRepositoryImpl(
    private val seedDao: SeedDao,
    private val roomVault: SeedKeyVault,
    private val trustyVault: SeedKeyVault,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SeedVaultRepository {
    override fun getSeeds(): Flow<List<VaultSeed>> = seedDao.getAllAsFlow().map { seeds -> seeds.map { it.toVaultSeed() } }

    override fun getSeed(seedId: Long): Flow<VaultSeed?> = getSeeds().map { seeds -> seeds.firstOrNull { it.id == seedId } }

    override suspend fun addSeed(
        seedPhrase: List<String>,
        isBackedUp: Boolean,
    ): VaultSeed =
        withContext(coroutineDispatcher) {
            val vault = if (trustyVault.isAvailable()) trustyVault else roomVault
            Log.i(TAG, "Storing the keys of a new seed in ${vault.storage}")
            val stored = vault.addSeed(seedPhrase)
            val id =
                seedDao.insertWithDefaultName(
                    seed =
                        SeedEntity(
                            name = "",
                            isBackedUp = isBackedUp,
                            encryptedEntropy = stored.encryptedEntropy,
                            storage = vault.storage,
                        ),
                    accounts =
                        stored.accounts.map {
                            SeedAccountEntity(
                                seedId = 0,
                                addressIndex = it.index,
                                address = it.address,
                                salt = it.salt,
                                publicKey = it.publicKey,
                                encryptedPrivateKey = it.encryptedPrivateKey,
                            )
                        },
                )
            val saved = checkNotNull(seedDao.get(id)) { "Seed $id was not saved" }
            if (saved.seed.storage != vault.storage) {
                // The phrase was already stored somewhere else, so the keys just added are not needed
                vault.deleteSeed(stored)
            }
            saved.toVaultSeed()
        }

    override suspend fun signBytes(
        address: String,
        data: ByteArray,
    ): ByteArray? =
        withContext(coroutineDispatcher) {
            val (seed, index) = seedOf(address) ?: return@withContext null
            vaultFor(seed.seed).signBytes(seed.toStoredSeed(), index, data)
        }

    override suspend fun signTransaction(
        address: String,
        transaction: ByteArray,
    ): ByteArray? =
        withContext(coroutineDispatcher) {
            val (seed, index) = seedOf(address) ?: return@withContext null
            vaultFor(seed.seed).signTransaction(seed.toStoredSeed(), index, transaction)
        }

    override suspend fun getSeedPhrase(seedId: Long): List<String>? =
        withContext(coroutineDispatcher) {
            val seed = seedDao.get(seedId) ?: return@withContext null
            vaultFor(seed.seed).revealSeedPhrase(seed.toStoredSeed())
        }

    override suspend fun getPublicKey(address: String): ByteArray? = seedDao.getPublicKey(address)

    override suspend fun authorizeApp(
        seedId: Long,
        packageName: String,
        label: String,
    ) {
        seedDao.insertAuthorizedApp(
            AuthorizedAppEntity(seedId = seedId, packageName = packageName, label = label, isPrivileged = false),
        )
    }

    override suspend fun renameSeed(
        seedId: Long,
        name: String,
    ) {
        seedDao.rename(seedId, name)
    }

    override suspend fun deleteSeed(seedId: Long) {
        withContext(coroutineDispatcher) {
            // The keys go first: if they cannot be removed the seed stays, so it can be tried again
            seedDao.get(seedId)?.let { vaultFor(it.seed).deleteSeed(it.toStoredSeed()) }
            seedDao.delete(seedId)
        }
    }

    override suspend fun revokeApp(
        seedId: Long,
        packageName: String,
    ) {
        seedDao.revokeApp(seedId, packageName)
    }

    /** The seed that has [address], with the index of the address, or null if no seed has it. */
    private suspend fun seedOf(address: String): Pair<SeedWithApps, Int>? {
        val row = seedDao.getAccount(address) ?: return null
        val seed = seedDao.get(row.seedId) ?: return null
        return seed to row.addressIndex
    }

    /** The vault that holds the keys of [seed]. */
    private suspend fun vaultFor(seed: SeedEntity): SeedKeyVault {
        val vault =
            when (seed.storage) {
                SeedStorage.ROOM -> roomVault
                SeedStorage.TRUSTY -> trustyVault
            }
        check(vault.isAvailable()) { "The keys of this seed are in ${seed.storage}, which is not available on this device" }
        return vault
    }

    private fun SeedWithApps.toStoredSeed() =
        StoredSeed(
            encryptedEntropy = seed.encryptedEntropy,
            accounts =
                accounts.map {
                    StoredAccount(
                        index = it.addressIndex,
                        address = it.address,
                        salt = it.salt,
                        publicKey = it.publicKey,
                        encryptedPrivateKey = it.encryptedPrivateKey,
                    )
                },
        )

    private fun SeedWithApps.toVaultSeed() =
        VaultSeed(
            id = seed.id,
            name = seed.name,
            isBackedUp = seed.isBackedUp,
            authorizedApps =
                apps.map {
                    AuthorizedApp(packageName = it.packageName, label = it.label, isPrivileged = it.isPrivileged)
                },
            addresses = accounts.sortedBy { it.addressIndex }.map { VaultAddress(index = it.addressIndex, address = it.address) },
        )

    private companion object {
        const val TAG = "SeedVaultRepository"
    }
}
