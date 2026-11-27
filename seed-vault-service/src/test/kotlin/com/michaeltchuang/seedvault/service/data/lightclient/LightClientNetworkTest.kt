package com.michaeltchuang.seedvault.service.data.lightclient

import org.junit.Assert.assertEquals
import org.junit.Test

class LightClientNetworkTest {
    @Test
    fun anchorsEndOnAnIntervalBoundaryAndTheLightClientStartsRightAfter() {
        LightClientNetwork.entries.forEach {
            assertEquals(0, it.anchorRound % LightClientNetwork.INTERVAL_SIZE)
            assertEquals(it.anchorRound + 1, it.firstAttestedRound)
        }
    }

    @Test
    fun intervalsCountFromTheFirstAttestedRound() {
        val network = LightClientNetwork.TESTNET
        val first = network.firstAttestedRound
        // Right after the start the next interval is number 0, which begins at the first attested round
        assertEquals(first, intervalStartRound(first, LightClientNetwork.INTERVAL_SIZE, 0))
        assertEquals(first + 3 * LightClientNetwork.INTERVAL_SIZE, intervalStartRound(first, LightClientNetwork.INTERVAL_SIZE, 3))
    }
}
