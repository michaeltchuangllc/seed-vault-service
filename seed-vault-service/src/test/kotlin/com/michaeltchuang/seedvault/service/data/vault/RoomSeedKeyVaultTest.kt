package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.model.AUTOSIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.model.HUMAN_SIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.vault.FalconKeyPair
import com.michaeltchuang.seedvault.service.domain.vault.FalconKeys
import com.michaeltchuang.seedvault.service.domain.vault.StoredAccount
import com.michaeltchuang.seedvault.service.domain.vault.StoredSeed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSeedKeyVaultTest {
    private val userAuthCipher = FakeSecretCipher("user-auth")
    private val autosignCipher = FakeSecretCipher("autosign")
    private val falcon = FakeFalconKeys()
    private val vault = RoomSeedKeyVault(userAuthCipher, autosignCipher, falcon)

    @Test
    fun entropyAndHumanSignKeyNeedTheUserAndAutosignKeyDoesNot() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)

            assertEquals("user-auth", FakeSecretCipher.tagOf(seed.encryptedEntropy))
            assertEquals("user-auth", FakeSecretCipher.tagOf(seed.account(HUMAN_SIGN_ADDRESS_INDEX).encryptedPrivateKey))
            assertEquals("autosign", FakeSecretCipher.tagOf(seed.account(AUTOSIGN_ADDRESS_INDEX).encryptedPrivateKey))
        }

    @Test
    fun addingASeedKeepsTheAddressesAndPublicKeyOfEachAccount() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)

            assertEquals(listOf(HUMAN_SIGN_ADDRESS_INDEX, AUTOSIGN_ADDRESS_INDEX), seed.accounts.map { it.index })
            assertEquals(listOf("ADDRESS0", "ADDRESS1"), seed.accounts.map { it.address })
            seed.accounts.forEach { assertArrayEquals(PUBLIC_KEY, it.publicKey) }
        }

    @Test
    fun autosignAccountSignsWhileTheUserIsNotVerified() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)
            userAuthCipher.locked = true

            val signature = vault.signBytes(seed, AUTOSIGN_ADDRESS_INDEX, DATA)

            assertArrayEquals(DATA.reversedArray(), signature)
        }

    @Test
    fun humanSignAccountNeedsTheUserToBeVerified() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)
            userAuthCipher.locked = true

            assertFailsWith<IllegalStateException> { vault.signBytes(seed, HUMAN_SIGN_ADDRESS_INDEX, DATA) }
            assertFailsWith<IllegalStateException> { vault.signTransaction(seed, HUMAN_SIGN_ADDRESS_INDEX, DATA) }

            userAuthCipher.locked = false
            assertArrayEquals(DATA.reversedArray(), vault.signBytes(seed, HUMAN_SIGN_ADDRESS_INDEX, DATA))
        }

    @Test
    fun revealingTheSeedPhraseNeedsTheUserToBeVerified() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)
            userAuthCipher.locked = true
            // Autosign being usable must not open the seed phrase
            autosignCipher.locked = false

            assertFailsWith<IllegalStateException> { vault.revealSeedPhrase(seed) }

            userAuthCipher.locked = false
            assertEquals(SEED_PHRASE, vault.revealSeedPhrase(seed))
        }

    @Test
    fun privateKeyAndEntropyAreWipedAfterUse() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)
            vault.signBytes(seed, HUMAN_SIGN_ADDRESS_INDEX, DATA)
            vault.revealSeedPhrase(seed)

            assertTrue(falcon.privateKeys.isNotEmpty())
            assertTrue(falcon.privateKeys.all { key -> key.all { it == 0.toByte() } })
            assertTrue(falcon.entropies.all { entropy -> entropy.all { it == 0.toByte() } })
        }

    @Test
    fun anUnreadableSecretIsNeverSignedWith() =
        runTest {
            val seed = vault.addSeed(SEED_PHRASE)
            // The autosign copy is not readable with the user auth key, and the other way round
            val swapped =
                StoredAccount(AUTOSIGN_ADDRESS_INDEX, "ADDRESS1", 1, PUBLIC_KEY, seed.account(HUMAN_SIGN_ADDRESS_INDEX).encryptedPrivateKey)

            assertFailsWith<IllegalArgumentException> {
                vault.signBytes(StoredSeed(seed.encryptedEntropy, listOf(swapped)), AUTOSIGN_ADDRESS_INDEX, DATA)
            }
        }

    private suspend inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("Expected ${T::class.simpleName} but got $failure", failure is T)
    }

    private class FakeFalconKeys : FalconKeys {
        val privateKeys = mutableListOf<ByteArray>()
        val entropies = mutableListOf<ByteArray>()

        override fun entropyFromSeedPhrase(seedPhrase: List<String>) = ENTROPY.copyOf().also(entropies::add)

        override fun seedPhraseFromEntropy(entropy: ByteArray): List<String> {
            assertArrayEquals(ENTROPY, entropy)
            return SEED_PHRASE
        }

        override fun keyPairFromEntropy(entropy: ByteArray) = FalconKeyPair(PUBLIC_KEY, PRIVATE_KEY.copyOf().also(privateKeys::add))

        override fun accounts(publicKey: ByteArray) =
            listOf(HUMAN_SIGN_ADDRESS_INDEX, AUTOSIGN_ADDRESS_INDEX).map {
                StoredAccount(it, "ADDRESS$it", it, publicKey, ByteArray(0))
            }

        override fun signBytes(
            privateKey: ByteArray,
            data: ByteArray,
        ): ByteArray {
            assertArrayEquals(PRIVATE_KEY, privateKey)
            privateKeys += privateKey
            return data.reversedArray()
        }

        override fun signTransaction(
            publicKey: ByteArray,
            privateKey: ByteArray,
            salt: Int,
            transaction: ByteArray,
        ) = signBytes(privateKey, transaction)
    }

    private companion object {
        val SEED_PHRASE = List(25) { "word$it" }
        val ENTROPY = ByteArray(32) { 7 }
        val PRIVATE_KEY = ByteArray(16) { 9 }
        val PUBLIC_KEY = ByteArray(16) { 3 }
        val DATA = byteArrayOf(1, 2, 3)
    }
}
