package com.michaeltchuang.seedvault.service.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `getLightClientStatus` and the other light client calls return, as JSON. Public chain data only.
 * `started` false means the other fields are 0 or empty.
 */
@Serializable
internal data class RemoteLightClientStatus(
    val network: String,
    val started: Boolean,
    /** True when the light client runs in the Seed Vault app's process (no Trusty applet on this device), not in the TEE. */
    val emulated: Boolean = false,
    /** Everything up to this round is verified. */
    val verifiedUpToRound: Long = 0,
    /** First round of the oldest interval a transaction can still be verified in. */
    val earliestRound: Long = 0,
    /** First round of the next interval: the state proof to send next attests this round to nextRound + intervalSize - 1. */
    val nextRound: Long = 0,
    val intervalSize: Long = 0,
    val capacity: Long = 0,
    val lnProvenWeight: Long = 0,
    /** Base64 of the voters commitment the next state proof is checked against. */
    val votersCommitment: String = "",
    /** Last round of the state proof the light client was started from. */
    val anchorRound: Long = 0,
)

/** The `Message` of a state proof exactly as algod's `GET /v2/stateproofs/{round}` serves it, commitments in Base64. */
@Serializable
internal data class RemoteStateProofMessage(
    @SerialName("BlockHeadersCommitment") val blockHeadersCommitment: String,
    @SerialName("VotersCommitment") val votersCommitment: String,
    @SerialName("LnProvenWeight") val lnProvenWeight: Long,
    @SerialName("FirstAttestedRound") val firstAttestedRound: Long,
    @SerialName("LastAttestedRound") val lastAttestedRound: Long,
)
