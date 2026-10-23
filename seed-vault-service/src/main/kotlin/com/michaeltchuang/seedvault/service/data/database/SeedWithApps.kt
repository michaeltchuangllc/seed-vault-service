package com.michaeltchuang.seedvault.service.data.database

import androidx.room.Embedded
import androidx.room.Relation

/** A seed row with its accounts and authorized apps, loaded together by Room. */
internal class SeedWithApps(
    @Embedded val seed: SeedEntity,
    @Relation(parentColumn = "id", entityColumn = "seed_id") val accounts: List<SeedAccountEntity>,
    @Relation(parentColumn = "id", entityColumn = "seed_id") val apps: List<AuthorizedAppEntity>,
)
