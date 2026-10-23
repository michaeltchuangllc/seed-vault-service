package com.michaeltchuang.seedvault.service.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.michaeltchuang.seedvault.service.data.vault.SeedStorage

/**
 * A seed in the vault. The Falcon-1024 entropy behind the 25-word seed phrase is stored encrypted
 * with the wallet-sdk-core Android Keystore key. The keys of its accounts are in [SeedAccountEntity].
 *
 * [storage] says where the secrets live. Room stores it as the enum name, so renaming a
 * [SeedStorage] value needs a migration. For a seed kept in the Trusty applet [encryptedEntropy]
 * and the accounts' encrypted private keys are empty.
 */
@Entity(tableName = "seed")
internal class SeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("name") val name: String,
    @ColumnInfo("is_backed_up") val isBackedUp: Boolean,
    @ColumnInfo("encrypted_entropy", typeAffinity = ColumnInfo.BLOB) val encryptedEntropy: ByteArray,
    @ColumnInfo("storage") val storage: SeedStorage,
)
