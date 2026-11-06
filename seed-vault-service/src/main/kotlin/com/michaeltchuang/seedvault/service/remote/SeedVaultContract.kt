package com.michaeltchuang.seedvault.service.remote

import kotlinx.serialization.Serializable

/** Names client apps use to reach the Seed Vault Service. */
object SeedVaultContract {
    const val PACKAGE = "com.michaeltchuang.seedvault.service"

    /** Permission a client app must hold to bind to the service. */
    const val PERMISSION_ACCESS_SEED_VAULT = "$PACKAGE.ACCESS_SEED_VAULT"

    /** Action to bind to the [ISeedVaultService] AIDL service. */
    const val ACTION_SEED_VAULT_SERVICE = "$PACKAGE.SEED_VAULT_SERVICE"

    /** Action of the activity where the user authorizes the calling app. Start it with startActivityForResult. */
    const val ACTION_AUTHORIZE_APP = "$PACKAGE.AUTHORIZE_APP"

    /**
     * Action of the activity where the user approves a signature and verifies themselves. Start it
     * with startActivityForResult with the values you are about to pass to the sign method, then
     * call that method after RESULT_OK. Extras: [EXTRA_ADDRESS] (the signing account) and either [EXTRA_DATA] (signBytes)
     * or [EXTRA_TRANSACTION] (signTransaction). For deriveKeyMaterial send [EXTRA_ADDRESS], [EXTRA_DOMAIN],
     * [EXTRA_INFO] and [EXTRA_LENGTH] instead.
     */
    const val ACTION_CONFIRM_SIGN = "$PACKAGE.CONFIRM_SIGN"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_DATA = "data"
    const val EXTRA_TRANSACTION = "transaction"

    /** Base64 transactions (a string array list) approved with one confirmation, then signed one by one with signTransaction. */
    const val EXTRA_TRANSACTIONS = "transactions"
    const val EXTRA_DOMAIN = "domain"
    const val EXTRA_INFO = "info"
    const val EXTRA_LENGTH = "length"
}

/** A seed as returned by `getAuthorizedSeeds`, with every account it has, by address index. */
@Serializable
data class RemoteSeed(
    val id: Long,
    val name: String,
    val accounts: List<RemoteAccount>,
)

/**
 * One Falcon-1024 account of a seed. [publicKey] is Base64 encoded.
 *
 * @param isAutosign the account can sign without the user while they allow autosign
 * @param requiresApproval whether signing needs the confirm sign screen, as of this call. The service
 * checks again on every signature, since the user can change autosign at any time
 * @param supportsKeyDerivation the account can be used with deriveKeyMaterial. False with an older Seed Vault app
 */
@Serializable
data class RemoteAccount(
    val index: Int,
    val address: String,
    val publicKey: String,
    val isAutosign: Boolean = false,
    val requiresApproval: Boolean = true,
    val supportsKeyDerivation: Boolean = false,
)
