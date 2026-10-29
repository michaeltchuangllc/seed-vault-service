package com.michaeltchuang.seedvault.service.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One account of a seed: its address, the PQ address salt behind it, and its Falcon-1024 key pair.
 * Every [addressIndex] has its own key pair. Index 0 is the human sign account other wallets
 * restore, index 1 the autosign account. An address is unique, so the same seed phrase is
 * only stored once.
 *
 * [publicKey] is not secret and is stored as is. [encryptedPrivateKey] is encrypted with the
 * wallet-sdk-core Android Keystore key, and empty when the seed's storage is the Trusty applet.
 */
@Entity(
    tableName = "seed_account",
    primaryKeys = ["seed_id", "address_index"],
    indices = [Index(value = ["address"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["seed_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal class SeedAccountEntity(
    @ColumnInfo("seed_id") val seedId: Long,
    @ColumnInfo("address_index") val addressIndex: Int,
    @ColumnInfo("address") val address: String,
    @ColumnInfo("salt") val salt: Int,
    @ColumnInfo("public_key", typeAffinity = ColumnInfo.BLOB) val publicKey: ByteArray,
    @ColumnInfo("encrypted_private_key", typeAffinity = ColumnInfo.BLOB) val encryptedPrivateKey: ByteArray,
) {
    fun withSeedId(seedId: Long) = SeedAccountEntity(seedId, addressIndex, address, salt, publicKey, encryptedPrivateKey)
}
