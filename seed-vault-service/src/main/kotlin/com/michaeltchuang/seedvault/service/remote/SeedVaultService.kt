package com.michaeltchuang.seedvault.service.remote

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import com.michaeltchuang.seedvault.service.ISeedVaultService
import com.michaeltchuang.seedvault.service.data.settings.AutosignSettings
import com.michaeltchuang.seedvault.service.domain.model.AUTOSIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.model.VaultSeed
import com.michaeltchuang.seedvault.service.domain.usecase.GetPublicKey
import com.michaeltchuang.seedvault.service.domain.usecase.GetSeeds
import com.michaeltchuang.seedvault.service.domain.usecase.SignBytes
import com.michaeltchuang.seedvault.service.domain.usecase.SignTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

/**
 * Bound service behind [ISeedVaultService]. Every call is checked against the app that made it:
 * it needs the ACCESS_SEED_VAULT permission, and can only see and sign with the accounts of seeds it
 * was authorized for by the user. The app is identified by its Binder uid, never by anything it passes in.
 *
 * Signing with a seed's human sign address always needs an approval from the confirm sign screen.
 * Signing with its autosign address does not while the user allows autosign, and does otherwise.
 */
class SeedVaultService : Service() {
    private val getSeeds: GetSeeds by inject()
    private val getPublicKey: GetPublicKey by inject()
    private val signBytesUseCase: SignBytes by inject()
    private val signTransactionUseCase: SignTransaction by inject()
    private val signApprovals: SignApprovalCoordinator by inject()
    private val transactionDecoder: TransactionDecoder by inject()
    private val autosignSettings: AutosignSettings by inject()

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder =
        object : ISeedVaultService.Stub() {
            override fun isServiceReady(): Boolean {
                enforceCallerPermission()
                return true
            }

            override fun getAuthorizedSeeds(): String {
                val packages = callerPackages()
                return runBlocking(Dispatchers.IO) {
                    val seeds = authorizedSeeds(packages).map { it.toRemoteSeed() }
                    Json.encodeToString(seeds)
                }
            }

            override fun signBytes(
                address: String,
                data: ByteArray,
            ): ByteArray? {
                val packages = callerPackages()
                return runBlocking(Dispatchers.IO) {
                    val index = requireAuthorized(packages, address)
                    requireUserApproval(packages, index, SignOperation.bytes(address, data))
                    signBytesUseCase(address, data)
                }
            }

            override fun signTransaction(
                address: String,
                transaction: ByteArray,
            ): ByteArray? {
                val packages = callerPackages()
                // A malformed request is the caller's mistake, not a signing failure
                val decoded =
                    transactionDecoder.decode(transaction)
                        ?: throw IllegalArgumentException("The bytes are not an Algorand transaction")
                require(decoded.sender == address) { "The transaction sender is not $address" }
                return runBlocking(Dispatchers.IO) {
                    val index = requireAuthorized(packages, address)
                    requireUserApproval(packages, index, SignOperation.transaction(address, transaction))
                    signTransactionUseCase(address, transaction)
                }
            }
        }

    private fun enforceCallerPermission() {
        if (checkCallingPermission(SeedVaultContract.PERMISSION_ACCESS_SEED_VAULT) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Requires ${SeedVaultContract.PERMISSION_ACCESS_SEED_VAULT}")
        }
    }

    /** Packages that share the uid of the caller. Only valid on the binder thread that was called. */
    private fun callerPackages(): Set<String> {
        enforceCallerPermission()
        return packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty().toSet()
    }

    /** The address index of [address] in a seed the caller is authorized for. */
    private suspend fun requireAuthorized(
        packages: Set<String>,
        address: String,
    ): Int =
        authorizedSeeds(packages).firstNotNullOfOrNull { it.indexOf(address) }
            ?: throw SecurityException("The calling app is not authorized for address $address")

    /**
     * Every signature needs one the user approved on the confirm sign screen, which the client starts
     * first. The exception is the autosign address while the user allows autosign.
     */
    private fun requireUserApproval(
        packages: Set<String>,
        addressIndex: Int,
        operation: SignOperation,
    ) {
        if (!requiresApproval(addressIndex)) return
        if (!signApprovals.consume(packages, operation)) {
            throw SecurityException("The user has not approved this signature. Start ${SeedVaultContract.ACTION_CONFIRM_SIGN} first")
        }
    }

    /** Whether a signature with the account at [addressIndex] needs the confirm sign screen right now. */
    private fun requiresApproval(addressIndex: Int): Boolean = !(addressIndex == AUTOSIGN_ADDRESS_INDEX && autosignSettings.isEnabled.value)

    private suspend fun authorizedSeeds(packages: Set<String>): List<VaultSeed> =
        getSeeds()
            .first()
            .filter { seed -> seed.authorizedApps.any { it.packageName in packages } }

    private suspend fun VaultSeed.toRemoteSeed() =
        RemoteSeed(
            id = id,
            name = name,
            accounts =
                addresses.map { account ->
                    val publicKey =
                        checkNotNull(getPublicKey(account.address)) { "No public key for ${account.address}" }
                    RemoteAccount(
                        index = account.index,
                        address = account.address,
                        publicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP),
                        isAutosign = account.isAutosign,
                        requiresApproval = requiresApproval(account.index),
                    )
                },
        )
}
