package com.michaeltchuang.seedvault.service.domain.model

/**
 * Address index of the standard account of a seed, the one other wallets restore from the seed
 * phrase. Every signature needs the user. Same value as the Trusty applet's `humanSignIndex()`.
 */
const val HUMAN_SIGN_ADDRESS_INDEX = 0

/**
 * Address index of the autosign account of a seed: the same key, next PQ-compliant salt. When the
 * user allows autosign, apps authorized for the seed can sign with it without asking. Same value as
 * the Trusty applet's `autosignIndex()`.
 */
const val AUTOSIGN_ADDRESS_INDEX = 1

/** The address indexes every seed gets, in order. */
val SEED_ADDRESS_INDEXES = listOf(HUMAN_SIGN_ADDRESS_INDEX, AUTOSIGN_ADDRESS_INDEX)

/** One Falcon-1024 address of a seed. All accounts of a seed are authorized by its single key. */
data class VaultAddress(
    val index: Int,
    val address: String,
) {
    val isAutosign get() = index == AUTOSIGN_ADDRESS_INDEX
}

/**
 * A seed stored in the vault, as shown on the Seed Vault settings screens.
 * Only display data lives here: no seed phrase, private key or derivation material.
 *
 * @param addresses the Falcon-1024 accounts of the seed, by index
 */
data class VaultSeed(
    val id: Long,
    val name: String,
    val isBackedUp: Boolean,
    val authorizedApps: List<AuthorizedApp>,
    val addresses: List<VaultAddress> = emptyList(),
) {
    /** The human sign address, or empty for a seed that has none. */
    val address: String get() = addressAt(HUMAN_SIGN_ADDRESS_INDEX).orEmpty()

    /** The autosign address, or null for a seed that has none. */
    val autosignAddress: String? get() = addressAt(AUTOSIGN_ADDRESS_INDEX)

    fun addressAt(index: Int): String? = addresses.firstOrNull { it.index == index }?.address

    /** The index of [address] in this seed, or null if it is not one of its accounts. */
    fun indexOf(address: String): Int? = addresses.firstOrNull { it.address == address }?.index
}

/**
 * An app that is allowed to request signatures from a seed.
 *
 * @param isPrivileged privileged wallets (e.g. holding ACCESS_SEED_VAULT_PRIVILEGED) cannot be
 * revoked from the settings screen, which matches the disabled "Authorized" button in the design.
 */
data class AuthorizedApp(
    val packageName: String,
    val label: String,
    val isPrivileged: Boolean = false,
)
