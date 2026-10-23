package com.michaeltchuang.seedvault.sdk.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.michaeltchuang.seedvault.sdk.SeedVaultServiceConstants
import com.michaeltchuang.seedvault.sdk.domain.model.SeedVaultSeed
import com.michaeltchuang.seedvault.service.ISeedVaultService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client helper for binding to Seed Vault Service over AIDL.
 */
class SeedVaultServiceClient(
    private val context: Context,
) {
    private var service: ISeedVaultService? = null
    private var connection: ServiceConnection? = null

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Bind to the service, or return the existing connection.
     * Throws if the service app is not installed or this app lacks the ACCESS_SEED_VAULT permission.
     */
    suspend fun bindAsync(): ISeedVaultService =
        suspendCancellableCoroutine { continuation ->
            service?.let {
                continuation.resume(it)
                return@suspendCancellableCoroutine
            }

            val newConnection =
                object : ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        binder: IBinder?,
                    ) {
                        Log.d(TAG, "Service connected")
                        val svc = ISeedVaultService.Stub.asInterface(binder)
                        service = svc
                        if (continuation.isActive) continuation.resume(svc)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.d(TAG, "Service disconnected")
                        service = null
                    }
                }

            val intent =
                Intent(SeedVaultServiceConstants.ACTION_SEED_VAULT_SERVICE).apply {
                    setPackage(SeedVaultServiceConstants.PACKAGE)
                }
            try {
                if (context.bindService(intent, newConnection, Context.BIND_AUTO_CREATE)) {
                    connection = newConnection
                    continuation.invokeOnCancellation { unbind() }
                } else {
                    // bindService keeps a pending connection even when it returns false
                    context.unbindService(newConnection)
                    continuation.resumeWithException(
                        IllegalStateException("Could not bind to Seed Vault Service"),
                    )
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }

    fun unbind() {
        val current = connection ?: return
        try {
            context.unbindService(current)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Not bound to service", e)
        }
        connection = null
        service = null
    }

    fun isConnected(): Boolean = service != null

    private fun requireService(): ISeedVaultService =
        service ?: throw IllegalStateException("Not connected to Seed Vault Service. Call bindAsync() first.")

    /** Seeds the user has authorized this app to use. */
    fun getAuthorizedSeeds(): List<SeedVaultSeed> = json.decodeFromString(requireService().authorizedSeeds)

    /**
     * Sign [data] with the Falcon-1024 key of the account at [address]. Throws SecurityException if
     * this app is not authorized for the address.
     */
    fun signBytes(
        address: String,
        data: ByteArray,
    ): ByteArray? = requireService().signBytes(address, data)

    /**
     * Sign an unsigned Algorand [transaction] (msgpack, as built by [AlgorandTransactions] or any other
     * builder) with the account at [address], which must be its sender. Returns the signed transaction,
     * ready to send to algod. Throws SecurityException if this app is not authorized for the address or
     * the user has not approved this transaction, IllegalArgumentException if the bytes are not a
     * transaction from [address].
     */
    fun signTransaction(
        address: String,
        transaction: ByteArray,
    ): ByteArray? = requireService().signTransaction(address, transaction)

    /** Intent for the authorization screen. Start it with startActivityForResult. */
    fun authorizeAppIntent(): Intent =
        Intent(SeedVaultServiceConstants.ACTION_AUTHORIZE_APP).apply {
            setPackage(SeedVaultServiceConstants.PACKAGE)
        }

    /**
     * Intent for the screen where the user approves signing [data] and verifies themselves. Start it
     * with startActivityForResult and call [signBytes] with the same values after RESULT_OK.
     */
    fun confirmSignBytesIntent(
        address: String,
        data: ByteArray,
    ): Intent = confirmSignIntent(address).putExtra(SeedVaultServiceConstants.EXTRA_DATA, data)

    /**
     * Same for [signTransaction]: the user is shown what the transaction does. Pass the same
     * [transaction] to [signTransaction] after RESULT_OK.
     */
    fun confirmSignTransactionIntent(
        address: String,
        transaction: ByteArray,
    ): Intent = confirmSignIntent(address).putExtra(SeedVaultServiceConstants.EXTRA_TRANSACTION, transaction)

    private fun confirmSignIntent(address: String): Intent =
        Intent(SeedVaultServiceConstants.ACTION_CONFIRM_SIGN)
            .setPackage(SeedVaultServiceConstants.PACKAGE)
            .putExtra(SeedVaultServiceConstants.EXTRA_ADDRESS, address)

    private companion object {
        const val TAG = "SeedVaultServiceClient"
    }
}
