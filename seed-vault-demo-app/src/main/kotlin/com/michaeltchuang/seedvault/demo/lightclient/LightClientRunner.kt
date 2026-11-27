package com.michaeltchuang.seedvault.demo.lightclient

import android.content.Context
import com.michaeltchuang.seedvault.sdk.client.SeedVaultServiceClient
import com.michaeltchuang.seedvault.sdk.data.repository.AlgodStateProofFetcher
import com.michaeltchuang.seedvault.sdk.data.repository.LightClientRepository
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientCheckResult
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientNetwork
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientStatus
import kotlinx.coroutines.CancellationException

/**
 * Binds to Seed Vault Service, checks the light clients through the SDK and writes the result to the
 * [LightClientLog]. The background job and the "Verify now" button both use it. This app talks to algod,
 * the Seed Vault app has no network access and its Trusty applet verifies every state proof.
 */
class LightClientRunner(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val log = LightClientLog.get(appContext)

    /** Checks each of [networks] in turn. A failure on one is logged and does not stop the next. */
    suspend fun check(networks: List<LightClientNetwork> = LightClientNetwork.entries) {
        val client = SeedVaultServiceClient(appContext)
        val fetcher = AlgodStateProofFetcher()
        try {
            val bound = runCatching { client.bindAsync() }
            val repository = LightClientRepository(client, fetcher)
            networks.forEach { network ->
                val message =
                    bound.exceptionOrNull()?.let { "Check failed: Seed Vault Service is not reachable (${it.message})" }
                        ?: try {
                            "Checked: " + describe(repository.check(network))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            "Check failed: ${e.message ?: e::class.java.simpleName}"
                        }
                log.record(network, LightClientCheck(now(), message))
            }
        } finally {
            client.unbind()
            fetcher.close()
        }
    }

    /** Starts the light client of [network] again from its anchor, then checks it so it catches up. */
    suspend fun reset(network: LightClientNetwork) {
        val client = SeedVaultServiceClient(appContext)
        val fetcher = AlgodStateProofFetcher()
        try {
            val message =
                try {
                    client.bindAsync()
                    val repository = LightClientRepository(client, fetcher)
                    val started = repository.reset(network)
                    "Reset: started again at round ${started.nextRound}, " + describe(repository.check(network))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    "Reset failed: ${e.message ?: e::class.java.simpleName}"
                }
            log.record(network, LightClientCheck(now(), message))
        } finally {
            client.unbind()
            fetcher.close()
        }
    }

    /** What the light client of each network has verified, or why it cannot be read. */
    suspend fun status(): Map<LightClientNetwork, Result<LightClientStatus>> {
        val client = SeedVaultServiceClient(appContext)
        val fetcher = AlgodStateProofFetcher()
        try {
            client.bindAsync()
            val repository = LightClientRepository(client, fetcher)
            return LightClientNetwork.entries.associateWith { runCatching { repository.status(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return LightClientNetwork.entries.associateWith { Result.failure(e) }
        } finally {
            client.unbind()
            fetcher.close()
        }
    }

    private fun describe(result: LightClientCheckResult) =
        when (result) {
            is LightClientCheckResult.Advanced ->
                "verified ${result.proofs} new state proof(s), now up to round ${result.verifiedUpToRound}"
            is LightClientCheckResult.UpToDate ->
                "no newer state proof, verified up to round ${result.verifiedUpToRound}"
        }
}
