package com.michaeltchuang.seedvault.service.remote

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import com.michaeltchuang.seedvault.service.ISeedVaultService
import com.michaeltchuang.seedvault.service.domain.model.VaultSeed
import com.michaeltchuang.seedvault.service.domain.repository.SeedVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

/**
 * Bound service behind [ISeedVaultService]. Every call is checked against the app that made it:
 * it needs the ACCESS_SEED_VAULT permission, and can only see and sign with the addresses of seeds it
 * was authorized for by the user. The app is identified by its Binder uid, never by anything it passes in.
 */
class SeedVaultService : Service() {
    private val seedVaultRepository: SeedVaultRepository by inject()
    private val signApprovals: SignApprovalCoordinator by inject()
    private val transactionDecoder: TransactionDecoder by inject()

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
                    val seed = requireAuthorized(packages, address)
                    requireUserApproval(packages, SignOperation.bytes(address, data))
                    seedVaultRepository.signBytes(seed.id, data)
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
                    val seed = requireAuthorized(packages, address)
                    requireUserApproval(packages, SignOperation.transaction(address, transaction))
                    seedVaultRepository.signTransaction(seed.id, transaction)
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

    private suspend fun requireAuthorized(
        packages: Set<String>,
        address: String,
    ): VaultSeed =
        authorizedSeeds(packages).firstOrNull { it.address == address }
            ?: throw SecurityException("The calling app is not authorized for address $address")

    /** Every signature needs one the user approved on the confirm sign screen, which the client starts first. */
    private fun requireUserApproval(
        packages: Set<String>,
        operation: SignOperation,
    ) {
        if (!signApprovals.consume(packages, operation)) {
            throw SecurityException("The user has not approved this signature. Start ${SeedVaultContract.ACTION_CONFIRM_SIGN} first")
        }
    }

    private suspend fun authorizedSeeds(packages: Set<String>): List<VaultSeed> =
        seedVaultRepository
            .getSeeds()
            .first()
            .filter { seed -> seed.authorizedApps.any { it.packageName in packages } }

    private suspend fun VaultSeed.toRemoteSeed() =
        RemoteSeed(
            id = id,
            name = name,
            address = address,
            publicKey = Base64.encodeToString(checkNotNull(seedVaultRepository.getPublicKey(id)), Base64.NO_WRAP),
        )
}
