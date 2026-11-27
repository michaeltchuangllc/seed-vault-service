package com.michaeltchuang.seedvault.sdk.data.repository

import com.michaeltchuang.seedvault.sdk.client.SeedVaultServiceClient
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientCheckResult
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientNetwork
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientStatus
import com.michaeltchuang.seedvault.sdk.domain.model.StateProof
import com.michaeltchuang.seedvault.sdk.domain.model.StateProofMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Keeps the Seed Vault light client up to date. The vault does not use the network, so this fetches the
 * state proofs from algod and hands them to the vault, whose Trusty applet verifies each one before it trusts
 * the chain further. A node that sends a wrong proof is only refused.
 *
 * Algorand makes a state proof every 256 rounds, about 12 minutes, so call [check] about every 15 minutes.
 * [client] must be bound ([SeedVaultServiceClient.bindAsync]) and the app needs the ACCESS_SEED_VAULT permission.
 */
class LightClientRepository(
    private val client: SeedVaultServiceClient,
    private val fetcher: StateProofFetcher = AlgodStateProofFetcher(),
) {
    /** What the light client of [network] has verified so far. */
    suspend fun status(network: LightClientNetwork): LightClientStatus =
        withContext(Dispatchers.IO) { client.getLightClientStatus(network.name) }

    /** Starts the light client of [network] again from the state proof the vault knows, forgetting what it verified. */
    suspend fun reset(network: LightClientNetwork): LightClientStatus =
        withContext(Dispatchers.IO) { client.resetLightClient(network.name) }

    /**
     * Starts the light client of [network] if it is not running, then verifies the state proofs that are final
     * since the last one, oldest first, at most [maxProofs] so a long way behind finishes in a few calls.
     */
    suspend fun check(
        network: LightClientNetwork,
        maxProofs: Int = DEFAULT_MAX_PROOFS,
    ): LightClientCheckResult =
        withContext(Dispatchers.IO) {
            var status = client.getLightClientStatus(network.name)
            if (!status.started) status = client.startLightClient(network.name)
            var proofs = 0
            while (proofs < maxProofs) {
                val lastRound = status.nextRound + status.intervalSize - 1
                val proof = fetcher.fetch(network, lastRound) ?: break
                status = client.advanceLightClient(network.name, proof)
                proofs++
            }
            if (proofs ==
                0
            ) {
                LightClientCheckResult.UpToDate(status.verifiedUpToRound)
            } else {
                LightClientCheckResult.Advanced(proofs, status.verifiedUpToRound)
            }
        }

    companion object {
        const val DEFAULT_MAX_PROOFS = 8
    }
}

fun interface StateProofFetcher {
    /** The state proof whose last attested round is [lastAttestedRound], or null while there is none yet. */
    suspend fun fetch(
        network: LightClientNetwork,
        lastAttestedRound: Long,
    ): StateProof?
}

/**
 * Reads state proofs from the public algod node and indexer of the network.
 *
 * algod's `/v2/stateproofs/{round}` only has about the last 3 intervals (around 50 minutes), and the light client
 * starts from a fixed point in the past, so for older ones it reads the proof from the block that holds it: the
 * indexer finds the `stpf` transaction of the interval and algod serves that block (see [StateProofBlockReader]).
 * Either way the Seed Vault applet verifies the proof, so none of this has to be trusted.
 */
class AlgodStateProofFetcher : StateProofFetcher {
    private val httpClient =
        HttpClient(Android) {
            engine {
                connectTimeout = 30_000
                socketTimeout = 60_000
            }
        }

    override suspend fun fetch(
        network: LightClientNetwork,
        lastAttestedRound: Long,
    ): StateProof? =
        withContext(Dispatchers.IO) {
            val response: HttpResponse = httpClient.get("${network.algodUrl}/v2/stateproofs/$lastAttestedRound")
            val body = response.bodyAsText()
            when {
                response.status.isSuccess() -> parse(body)
                // Not made yet
                response.status == HttpStatusCode.NotFound -> null
                // algod answers 500 both for a round it has not reached and for one it no longer keeps
                body.contains(NOT_REACHED) -> null
                body.contains(NOT_KEPT) -> fromBlock(network, lastAttestedRound)
                else -> error("State proof request failed: ${response.status.value} $body")
            }
        }

    /** The proof of the interval that ends at [lastAttestedRound], from the block that confirmed it. */
    private suspend fun fromBlock(
        network: LightClientNetwork,
        lastAttestedRound: Long,
    ): StateProof? {
        val round = confirmedRound(network, lastAttestedRound) ?: return null
        val response: HttpResponse = httpClient.get("${network.algodUrl}/v2/blocks/$round?format=msgpack")
        check(response.status.isSuccess()) { "Block $round request failed: ${response.status.value}" }
        val proof = checkNotNull(StateProofBlockReader.read(response.body<ByteArray>())) { "Block $round has no state proof" }
        check(proof.message.lastAttestedRound == lastAttestedRound) {
            "Block $round has the state proof for round ${proof.message.lastAttestedRound}, not $lastAttestedRound"
        }
        return proof
    }

    /** The round of the block holding the `stpf` transaction that attests up to [lastAttestedRound], from the indexer. */
    private suspend fun confirmedRound(
        network: LightClientNetwork,
        lastAttestedRound: Long,
    ): Long? {
        // The proof is confirmed a bit after its interval (about 130 rounds), and the one after it a full interval later
        val url =
            "${network.indexerUrl}/v2/transactions?tx-type=stpf&min-round=${lastAttestedRound + 1}" +
                "&max-round=${lastAttestedRound + SEARCH_ROUNDS}&limit=1"
        val response: HttpResponse = httpClient.get(url)
        check(response.status.isSuccess()) { "Indexer request failed: ${response.status.value} ${response.bodyAsText()}" }
        val transaction =
            json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject
                .getValue("transactions")
                .jsonArray
                .firstOrNull()
                ?.jsonObject ?: return null
        val attested =
            transaction
                .getValue("state-proof-transaction")
                .jsonObject
                .getValue("message")
                .jsonObject
                .getValue("latest-attested-round")
                .jsonPrimitive.content
                .toLong()
        // Proofs are confirmed in order, so the first one after the interval is its own, unless it is not there yet
        if (attested != lastAttestedRound) return null
        return transaction
            .getValue("confirmed-round")
            .jsonPrimitive.content
            .toLong()
    }

    fun close() = httpClient.close()

    companion object {
        private const val NOT_REACHED = "greater than the latest round"
        private const val NOT_KEPT = "ledger does not have entry"
        private const val SEARCH_ROUNDS = 1024L
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): StateProof {
            val response = json.decodeFromString<AlgodStateProofResponse>(body)
            return StateProof(response.message, Base64.getDecoder().decode(response.stateProof))
        }
    }
}

@Serializable
private class AlgodStateProofResponse(
    @SerialName("Message") val message: StateProofMessage,
    @SerialName("StateProof") val stateProof: String,
)
