package com.michaeltchuang.seedvault.sdk.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StateProofBlockReaderTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/lightclient/$name")) { name }.use { it.readBytes() }

    @Test
    fun readsTheStateProofOutOfTheBlockThatConfirmedIt() {
        // TestNet block 69588099 holds the state proof for rounds 69587713..69587968
        val proof = StateProofBlockReader.read(resource("testnet_block_69588099.msgpack"))

        assertNotNull(proof)
        assertEquals(69587713, proof!!.message.firstAttestedRound)
        assertEquals(69587968, proof.message.lastAttestedRound)
        assertEquals(2289220, proof.message.lnProvenWeight)
        // The same bytes algod's /v2/stateproofs/69587968 served while it still had them
        assertArrayEquals(resource("testnet_state_proof_69587968.bin"), proof.proof)
    }

    @Test
    fun theMessageMatchesWhatAlgodServes() {
        val proof = checkNotNull(StateProofBlockReader.read(resource("testnet_block_69588099.msgpack")))

        assertEquals(
            "u27HRbApzourVm/bueYv+xyaRCi+Kdis+KTGcGjGBJcUkGlwlTXjkq+jYaJshti4Tnt3KcCbfOUbnc3Xrd8soA==",
            proof.message.votersCommitment,
        )
        assertEquals(44, proof.message.blockHeadersCommitment.length)
    }

    @Test
    fun aBlockWithoutAStateProofGivesNull() {
        assertNull(StateProofBlockReader.read(resource("testnet_block_69588000_no_state_proof.msgpack")))
    }
}
