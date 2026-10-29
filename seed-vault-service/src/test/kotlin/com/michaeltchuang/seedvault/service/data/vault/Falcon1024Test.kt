package com.michaeltchuang.seedvault.service.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The pure part of [Falcon1024]. The key and address functions need the native library. */
class Falcon1024Test {
    // The seed vault applet's own known answers (algokit-core seed_vault `falcon_key_entropy_known_answers`)
    private val entropy = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun autosignEntropyMatchesTheTrustyAppletAtIndex1() {
        assertArrayEquals(
            hex("861f184961559dc6cfb0438fe17af14d3fb4a29f0ed54f2f6db2737c03b3a1c9"),
            Falcon1024.autosignEntropy(entropy, 1),
        )
    }

    @Test
    fun autosignEntropyMatchesTheTrustyAppletAtIndex2() {
        assertArrayEquals(
            hex("6ad325ce31b1911db75acfbcb712c4b04f8830146b5fe07dca629e28c558a268"),
            Falcon1024.autosignEntropy(entropy, 2),
        )
    }

    @Test
    fun theLargestIndexWorksAndOneMoreIsRefused() {
        Falcon1024.autosignEntropy(entropy, Falcon1024.MAX_ADDRESS_INDEX)
        assertThrows(IllegalArgumentException::class.java) { Falcon1024.autosignEntropy(entropy, Falcon1024.MAX_ADDRESS_INDEX + 1) }
    }

    @Test
    fun theHumanSignIndexHasNoDerivedEntropy() {
        assertThrows(IllegalArgumentException::class.java) { Falcon1024.autosignEntropy(entropy, 0) }
    }

    private fun hex(value: String) = ByteArray(value.length / 2) { value.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
