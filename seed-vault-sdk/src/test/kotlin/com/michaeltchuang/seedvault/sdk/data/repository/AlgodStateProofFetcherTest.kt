package com.michaeltchuang.seedvault.sdk.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AlgodStateProofFetcherTest {
    @Test
    fun readsTheMessageAndTheProofAlgodServes() {
        val body =
            """{"Message":{"BlockHeadersCommitment":"AQID","VotersCommitment":"BAUG","LnProvenWeight":2289220,
            |"FirstAttestedRound":69587457,"LastAttestedRound":69587712},"StateProof":"BwgJ","Other":1}
            """.trimMargin()

        val proof = AlgodStateProofFetcher.parse(body)

        assertEquals("AQID", proof.message.blockHeadersCommitment)
        assertEquals("BAUG", proof.message.votersCommitment)
        assertEquals(2289220, proof.message.lnProvenWeight)
        assertEquals(69587457, proof.message.firstAttestedRound)
        assertEquals(69587712, proof.message.lastAttestedRound)
        assertArrayEquals(byteArrayOf(7, 8, 9), proof.proof)
    }
}
