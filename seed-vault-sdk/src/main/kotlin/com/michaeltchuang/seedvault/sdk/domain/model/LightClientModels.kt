package com.michaeltchuang.seedvault.sdk.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An Algorand network the Seed Vault light client can follow, and the public algod node and indexer its state
 * proofs are read from (the indexer finds the ones algod no longer keeps).
 */
enum class LightClientNetwork(
    val label: String,
    val algodUrl: String,
    val indexerUrl: String,
) {
    TESTNET("TestNet", "https://testnet-api.4160.nodely.dev", "https://testnet-idx.4160.nodely.dev"),
    MAINNET("MainNet", "https://mainnet-api.4160.nodely.dev", "https://mainnet-idx.4160.nodely.dev"),
}

/**
 * What the Seed Vault light client has verified for a network. [started] false means it has not started yet
 * and every other number is 0.
 */
@Serializable
data class LightClientStatus(
    val network: String,
    val started: Boolean,
    /** True when it runs in the Seed Vault app's process because the device has no Trusty applet, so no TEE protects it. */
    val emulated: Boolean = false,
    /** Everything up to this round is verified. */
    val verifiedUpToRound: Long = 0,
    /** First round of the oldest interval a transaction can still be verified in. */
    val earliestRound: Long = 0,
    /** First round of the next interval, which the next state proof attests. */
    val nextRound: Long = 0,
    /** Rounds covered by one state proof. */
    val intervalSize: Long = 0,
    val capacity: Long = 0,
    val lnProvenWeight: Long = 0,
    /** Base64 of the voters commitment the next state proof is checked against. */
    val votersCommitment: String = "",
    /** Last round of the state proof the light client was started from, trusted on first use. */
    val anchorRound: Long = 0,
)

/** A state proof as algod serves it: the [message] the signers attested and the [proof] itself. */
class StateProof(
    val message: StateProofMessage,
    val proof: ByteArray,
)

/** Algod's state proof message. The commitments are Base64, as algod and the Seed Vault AIDL use them. */
@Serializable
data class StateProofMessage(
    @SerialName("BlockHeadersCommitment") val blockHeadersCommitment: String,
    @SerialName("VotersCommitment") val votersCommitment: String,
    @SerialName("LnProvenWeight") val lnProvenWeight: Long,
    @SerialName("FirstAttestedRound") val firstAttestedRound: Long,
    @SerialName("LastAttestedRound") val lastAttestedRound: Long,
)

/** What one [com.michaeltchuang.seedvault.sdk.data.repository.LightClientRepository.check] did. */
sealed interface LightClientCheckResult {
    val verifiedUpToRound: Long

    /** [proofs] new state proofs were verified. */
    data class Advanced(
        val proofs: Int,
        override val verifiedUpToRound: Long,
    ) : LightClientCheckResult

    /** algod has no newer state proof yet. */
    data class UpToDate(
        override val verifiedUpToRound: Long,
    ) : LightClientCheckResult
}
