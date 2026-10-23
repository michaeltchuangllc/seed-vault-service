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
     * or [EXTRA_TRANSACTION] (signTransaction).
     */
    const val ACTION_CONFIRM_SIGN = "$PACKAGE.CONFIRM_SIGN"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_DATA = "data"
    const val EXTRA_TRANSACTION = "transaction"
}

/** A seed as returned by `getAuthorizedSeeds`. [publicKey] is Base64 encoded. */
@Serializable
data class RemoteSeed(
    val id: Long,
    val name: String,
    val address: String,
    val publicKey: String,
)
