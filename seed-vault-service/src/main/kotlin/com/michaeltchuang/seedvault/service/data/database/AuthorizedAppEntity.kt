package com.michaeltchuang.seedvault.service.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "authorized_app",
    primaryKeys = ["seed_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = SeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["seed_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class AuthorizedAppEntity(
    @ColumnInfo("seed_id") val seedId: Long,
    @ColumnInfo("package_name") val packageName: String,
    @ColumnInfo("label") val label: String,
    @ColumnInfo("is_privileged") val isPrivileged: Boolean,
)
