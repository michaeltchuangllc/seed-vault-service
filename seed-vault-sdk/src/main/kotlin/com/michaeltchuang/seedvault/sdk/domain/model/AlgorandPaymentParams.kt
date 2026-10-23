package com.michaeltchuang.seedvault.sdk.domain.model

/** Network values from algod that go into an Algorand transaction. [genesisHash] is the decoded hash. */
class AlgorandPaymentParams(
    val genesisId: String,
    val genesisHash: ByteArray,
    val firstValid: Long,
    val lastValid: Long,
)
