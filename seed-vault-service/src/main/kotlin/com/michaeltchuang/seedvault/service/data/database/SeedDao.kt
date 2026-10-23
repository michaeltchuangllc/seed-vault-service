package com.michaeltchuang.seedvault.service.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SeedDao {
    @Transaction
    @Query("SELECT * FROM seed ORDER BY id")
    fun getAllAsFlow(): Flow<List<SeedWithApps>>

    @Query("SELECT * FROM seed_account WHERE address = :address")
    suspend fun getAccount(address: String): SeedAccountEntity?

    @Query("SELECT public_key FROM seed_account WHERE address = :address")
    suspend fun getPublicKey(address: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorizedApp(app: AuthorizedAppEntity)

    @Transaction
    @Query("SELECT * FROM seed WHERE id = :seedId")
    suspend fun get(seedId: Long): SeedWithApps?

    @Insert
    suspend fun insert(seed: SeedEntity): Long

    @Insert
    suspend fun insertAccounts(accounts: List<SeedAccountEntity>)

    /**
     * Inserts [seed] with its [accounts] and names it after its new id, e.g. "Seed 3". Ids are
     * never reused. If a seed with the same first address is already stored, nothing is changed and
     * its id is returned.
     */
    @Transaction
    suspend fun insertWithDefaultName(
        seed: SeedEntity,
        accounts: List<SeedAccountEntity>,
    ): Long {
        require(accounts.isNotEmpty()) { "A seed needs at least one account" }
        getAccount(accounts.first().address)?.let { return it.seedId }
        val id = insert(seed)
        insertAccounts(accounts.map { it.withSeedId(id) })
        rename(id, defaultSeedName(id))
        return id
    }

    @Query("UPDATE seed SET name = :name WHERE id = :seedId")
    suspend fun rename(
        seedId: Long,
        name: String,
    )

    @Query("DELETE FROM seed WHERE id = :seedId")
    suspend fun delete(seedId: Long)

    @Query("DELETE FROM authorized_app WHERE seed_id = :seedId AND package_name = :packageName")
    suspend fun revokeApp(
        seedId: Long,
        packageName: String,
    )
}

internal fun defaultSeedName(id: Long) = "Seed $id"
