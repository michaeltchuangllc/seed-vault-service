package com.michaeltchuang.seedvault.sdk.domain.model

import kotlinx.serialization.Serializable

/**
 * A Falcon-1024 seed this app is authorized to use in Seed Vault Service, with every account it
 * has, by address index. Today a seed has index 0 (human sign) and index 1 (autosign), but loop over
 * [accounts] rather than assume that.
 */
@Serializable
data class SeedVaultSeed(
    val id: Long,
    val name: String,
    val accounts: List<SeedVaultAccount> = emptyList(),
) {
    /** The account with [address], or null if this seed has none. */
    fun account(address: String): SeedVaultAccount? = accounts.firstOrNull { it.address == address }
}

/**
 * One Falcon-1024 account of a seed. Pass [address] to the sign methods to sign with it.
 *
 * @param publicKey Base64 encoded Falcon-1024 public key
 * @param isAutosign the account can sign without the confirm sign screen while the user allows
 * autosign in Seed Vault
 * @param requiresApproval whether signing needs the confirm sign screen, as of the last
 * `getAuthorizedSeeds`. The user can change autosign at any time and the service checks on every
 * signature, so be ready for a SecurityException even when this is false
 */
@Serializable
data class SeedVaultAccount(
    val index: Int,
    val address: String,
    val publicKey: String,
    val isAutosign: Boolean = false,
    val requiresApproval: Boolean = true,
)
