package com.michaeltchuang.seedvault.service.data.lightclient

/**
 * A network the light client can follow. The anchor is a state proof that was already final when this was
 * written: the light client starts right after the round it attests and then verifies every later proof
 * against the voters the previous one committed to, so each value below is the `Message` of
 * `GET /v2/stateproofs/{anchorRound}` and was checked against the voters commitment in the block header of
 * the following multiple of 256.
 *
 * The anchor is trusted on first use (it came from a public algod node), everything after it is verified.
 * The vault never talks to the network: apps send it the state proofs (see `ISeedVaultService.advanceLightClient`).
 */
internal enum class LightClientNetwork(
    val label: String,
    /** Base64 of the genesis hash, as algod reports it in `gh`. */
    val genesisHash: String,
    /** Last round the anchor state proof attests. The light client starts at the round after it. */
    val anchorRound: Long,
    /** `VotersCommitment` of the anchor state proof message: who signs the next proof. */
    val anchorVotersCommitment: String,
    /** `LnProvenWeight` of the anchor state proof message. */
    val anchorLnProvenWeight: Long,
) {
    TESTNET(
        label = "TestNet",
        genesisHash = "SGO1GKSzyE7IEPItTxCByw9x8FmnrCDexi9/cOUJOiI=",
        anchorRound = 69_587_712,
        anchorVotersCommitment = "u27HRbApzourVm/bueYv+xyaRCi+Kdis+KTGcGjGBJcUkGlwlTXjkq+jYaJshti4Tnt3KcCbfOUbnc3Xrd8soA==",
        anchorLnProvenWeight = 2_289_220,
    ),
    MAINNET(
        label = "MainNet",
        genesisHash = "wGHE2Pwdvd7S12BL5FaOP20EGYesN73ktiC1qzkkit8=",
        anchorRound = 67_276_288,
        anchorVotersCommitment = "KrzlsvTT5GeE/SWoLEbJiO/zp+0LqySQANhQVormCC5o2IHlkwNgGg4oZ6L8gd/OZr8kJCzWBV1K9MsjfJ7SZg==",
        anchorLnProvenWeight = 2_230_682,
    ),
    ;

    val firstAttestedRound: Long get() = anchorRound + 1

    companion object {
        /** A state proof covers this many rounds. */
        const val INTERVAL_SIZE = 256L

        /** How many intervals the program keeps block header commitments for. */
        const val CAPACITY = 16L
    }
}
