package com.michaeltchuang.seedvault.service.domain.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDerivationTest {
    private val signature = ByteArray(1280) { (it * 7).toByte() }
    private val info = "example.com".encodeToByteArray()

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")

        val okm = KeyDerivation.hkdf(ikm, salt, info, 42, "HmacSHA256")

        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun theSameInputsGiveTheSameBytes() {
        val first = KeyDerivation.derive(signature, "ADDR", "passkey/v1", info, 64)
        val second = KeyDerivation.derive(signature, "ADDR", "passkey/v1", info, 64)

        assertArrayEquals(first, second)
        assertEquals(64, first.size)
    }

    @Test
    fun aDifferentSignatureAddressDomainOrInfoGivesUnrelatedBytes() {
        val base = KeyDerivation.derive(signature, "ADDR", "passkey/v1", info, 64)

        assertFalse(base.contentEquals(KeyDerivation.derive(ByteArray(1280) { 1 }, "ADDR", "passkey/v1", info, 64)))
        assertFalse(base.contentEquals(KeyDerivation.derive(signature, "OTHER", "passkey/v1", info, 64)))
        assertFalse(base.contentEquals(KeyDerivation.derive(signature, "ADDR", "passkey/v2", info, 64)))
        assertFalse(base.contentEquals(KeyDerivation.derive(signature, "ADDR", "passkey/v1", "other".encodeToByteArray(), 64)))
    }

    @Test
    fun aShorterOutputIsThePrefixOfALongerOne() {
        val short = KeyDerivation.derive(signature, "ADDR", "passkey/v1", info, 32)
        val long = KeyDerivation.derive(signature, "ADDR", "passkey/v1", info, 64)

        assertArrayEquals(short, long.copyOf(32))
    }

    @Test
    fun splittingTheSameBytesDifferentlyGivesDifferentMessages() {
        val a = KeyDerivation.message("AB", "passkey/c", info)
        val b = KeyDerivation.message("A", "passkey/Bc", info)

        assertFalse(a.contentEquals(b))
    }

    @Test
    fun theSignedMessageNeverStartsWithAReservedSigningPrefix() {
        val message = KeyDerivation.message("ADDR", "passkey/v1", info)

        listOf("TX", "TG", "Program", "ProgData", "MX").forEach {
            assertFalse("Starts with $it", message.decodeToString().startsWith(it))
        }
        assertTrue(message.decodeToString().startsWith("algokit-seedvault-derive-v${KeyDerivation.VERSION}"))
    }

    @Test
    fun theSignatureIsNotChanged() {
        val copy = signature.copyOf()

        KeyDerivation.derive(signature, "ADDR", "passkey/v1", info, 32)

        assertArrayEquals(copy, signature)
    }

    @Test
    fun refusesWhatIsNotAPasskeyRequest() {
        assertRefused("sign/v1", info, 32)
        assertRefused("passkey/", info, 32)
        assertRefused("passkey/" + "x".repeat(KeyDerivation.MAX_DOMAIN_BYTES), info, 32)
        assertRefused("passkey/v1", ByteArray(KeyDerivation.MAX_INFO_BYTES + 1), 32)
        assertRefused("passkey/v1", info, KeyDerivation.MIN_LENGTH - 1)
        assertRefused("passkey/v1", info, KeyDerivation.MAX_LENGTH + 1)
        assertTrue(runCatching { KeyDerivation.requireValid("passkey/v1", info, 32) }.isSuccess)
    }

    @Test
    fun refusesAnEmptySignature() {
        val failure = runCatching { KeyDerivation.derive(ByteArray(0), "ADDR", "passkey/v1", info, 32) }.exceptionOrNull()

        assertTrue("Got $failure", failure is IllegalArgumentException)
    }

    private fun assertRefused(
        domain: String,
        info: ByteArray,
        length: Int,
    ) {
        val failure = runCatching { KeyDerivation.derive(signature, "ADDR", domain, info, length) }.exceptionOrNull()
        assertTrue("Expected a refusal for $domain, got $failure", failure is IllegalArgumentException)
    }

    private fun hex(value: String) = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
