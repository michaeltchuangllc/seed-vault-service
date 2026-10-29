package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.model.AUTOSIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.model.HUMAN_SIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.vault.StoredAccount
import com.michaeltchuang.seedvault.service.domain.vault.StoredSeed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.seed_vault_ffi.Account
import uniffi.seed_vault_ffi.AppletErrorCode
import uniffi.seed_vault_ffi.KeyAlgorithm
import uniffi.seed_vault_ffi.ListedAccount
import uniffi.seed_vault_ffi.ProtocolVersion
import uniffi.seed_vault_ffi.SeedVaultClientInterface
import uniffi.seed_vault_ffi.SeedVaultException
import uniffi.seed_vault_ffi.SeedVaultStatus
import uniffi.seed_vault_ffi.SignedArbitraryData
import uniffi.seed_vault_ffi.SignedTransaction

class TrustySeedKeyVaultTest {
    private val applet = FakeApplet()
    private val vault =
        TrustySeedKeyVault(
            clientFactory = { applet },
            appletIndexProvider = { APPLET_INDEXES.getValue(it) },
            humanAddressOf = { HUMAN_ADDRESS },
        )
    private val seedPhrase = List(25) { "word$it" }

    private fun storedSeed() =
        StoredSeed(
            encryptedEntropy = ByteArray(0),
            accounts = STORED_ACCOUNTS,
        )

    @Test
    fun isAvailableWhenTheAppletIsThere() =
        runTest {
            assertTrue(vault.isAvailable())
        }

    @Test
    fun isNotAvailableWithoutTheApplet() =
        runTest {
            applet.status = SeedVaultStatus.ServiceNotFound("No applet on this device")
            assertFalse(vault.isAvailable())
        }

    @Test
    fun isNotAvailableWhenTheClientFails() =
        runTest {
            val broken = TrustySeedKeyVault(clientFactory = { error("Native library did not load") })
            assertFalse(broken.isAvailable())
        }

    @Test
    fun addSeedImportsTheHumanSignAccountAndDerivesTheAutosignOne() =
        runTest {
            val stored = vault.addSeed(seedPhrase)

            assertEquals(listOf(seedPhrase.joinToString(" ")), applet.importedMnemonics)
            assertEquals(KeyAlgorithm.FALCON1024, applet.importedAlgorithm)
            assertEquals(listOf(HUMAN_SIGN_APPLET_INDEX), applet.importedIndexes)
            assertEquals(listOf(HUMAN_ADDRESS to AUTOSIGN_APPLET_INDEX), applet.derived)
            assertEquals(listOf(HUMAN_ADDRESS, AUTOSIGN_ADDRESS), stored.accounts.map { it.address })
        }

    @Test
    fun addSeedKeepsOnlyPublicValuesAndEachAccountsOwnPublicKey() =
        runTest {
            val stored = vault.addSeed(seedPhrase)

            assertArrayEquals(HUMAN_PUBLIC_KEY, stored.account(HUMAN_SIGN_ADDRESS_INDEX).publicKey)
            assertArrayEquals(AUTOSIGN_PUBLIC_KEY, stored.account(AUTOSIGN_ADDRESS_INDEX).publicKey)
            assertEquals(0, stored.encryptedEntropy.size)
            assertTrue(stored.accounts.all { it.encryptedPrivateKey.isEmpty() })
        }

    @Test
    fun addSeedStillAddsTheAutosignAccountWhenTheAppletAlreadyHasTheSeed() =
        runTest {
            applet.importError = SeedVaultException.Applet(AppletErrorCode.ACCOUNT_ALREADY_EXISTS)

            val stored = vault.addSeed(seedPhrase)

            assertEquals(listOf(HUMAN_ADDRESS to AUTOSIGN_APPLET_INDEX), applet.derived)
            assertEquals(listOf(HUMAN_ADDRESS, AUTOSIGN_ADDRESS), stored.accounts.map { it.address })
        }

    @Test
    fun addSeedFailsWhenTheAppletMadeOtherAddresses() =
        runTest {
            applet.accounts[0] = Account(KeyAlgorithm.FALCON1024, HUMAN_SIGN_APPLET_INDEX.toUInt(), "SOMEOTHERADDRESS")

            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { vault.addSeed(seedPhrase) }
            }
        }

    @Test
    fun revealSeedPhraseReturnsTheWords() =
        runTest {
            applet.mnemonic = seedPhrase.joinToString(" ")

            assertEquals(seedPhrase, vault.revealSeedPhrase(storedSeed()))
        }

    @Test
    fun signBytesSignsWithTheAccountOfTheAddressIndex() =
        runTest {
            val data = byteArrayOf(9, 8, 7)

            val signature = vault.signBytes(storedSeed(), AUTOSIGN_ADDRESS_INDEX, data)

            assertArrayEquals(data.reversedArray(), signature)
            assertEquals(AUTOSIGN_ADDRESS, applet.signedAddress)
            assertEquals(AUTOSIGN_APPLET_INDEX, applet.signedIndex)
        }

    @Test
    fun signTransactionSignsWithTheHumanSignAccount() =
        runTest {
            vault.signTransaction(storedSeed(), HUMAN_SIGN_ADDRESS_INDEX, byteArrayOf(1))

            assertEquals(HUMAN_ADDRESS, applet.signedAddress)
            assertEquals(HUMAN_SIGN_APPLET_INDEX, applet.signedIndex)
        }

    @Test
    fun signBytesTurnsAReservedPrefixIntoAnIllegalArgument() =
        runTest {
            applet.signError = SeedVaultException.Applet(AppletErrorCode.RESERVED_DOMAIN_PREFIX)

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { vault.signBytes(storedSeed(), HUMAN_SIGN_ADDRESS_INDEX, byteArrayOf(1)) }
            }
        }

    @Test
    fun signBytesKeepsOtherAppletErrors() =
        runTest {
            applet.signError = SeedVaultException.Applet(AppletErrorCode.SIGNING_FAILED)

            assertThrows(SeedVaultException.Applet::class.java) {
                kotlinx.coroutines.runBlocking { vault.signBytes(storedSeed(), HUMAN_SIGN_ADDRESS_INDEX, byteArrayOf(1)) }
            }
        }

    @Test
    fun deleteSeedRemovesTheSeedByItsHumanSignAddress() =
        runTest {
            vault.deleteSeed(storedSeed())

            // The applet removes the autosign account with it, and refuses to delete one by its address
            assertEquals(listOf(HUMAN_ADDRESS), applet.deleted)
        }

    @Test
    fun deleteSeedIgnoresAnAccountThatIsAlreadyGone() =
        runTest {
            applet.deleteError = SeedVaultException.Applet(AppletErrorCode.ACCOUNT_NOT_FOUND)

            vault.deleteSeed(storedSeed())
        }

    @Test
    fun deleteSeedFailsForOtherAppletErrors() =
        runTest {
            applet.deleteError = SeedVaultException.Applet(AppletErrorCode.STORE_FAILURE)

            assertThrows(SeedVaultException.Applet::class.java) {
                kotlinx.coroutines.runBlocking { vault.deleteSeed(storedSeed()) }
            }
        }

    private class FakeApplet : SeedVaultClientInterface {
        var status: SeedVaultStatus = SeedVaultStatus.Available(ProtocolVersion(1u, 2u))
        var mnemonic = ""
        var importError: SeedVaultException? = null
        var deleteError: SeedVaultException? = null
        var signError: SeedVaultException? = null
        var signedAddress: String? = null
        var signedIndex: UByte? = null
        val importedMnemonics = mutableListOf<String>()
        var importedAlgorithm: KeyAlgorithm? = null
        val importedIndexes = mutableListOf<UByte>()
        val deleted = mutableListOf<String>()
        val derived = mutableListOf<Pair<String, UByte>>()

        val accounts =
            mutableListOf(
                Account(KeyAlgorithm.FALCON1024, HUMAN_SIGN_APPLET_INDEX.toUInt(), HUMAN_ADDRESS),
                Account(KeyAlgorithm.FALCON1024, AUTOSIGN_APPLET_INDEX.toUInt(), AUTOSIGN_ADDRESS),
            )

        private fun accountAt(addressIndex: UByte) = accounts.first { it.addressIndex == addressIndex.toUInt() }

        private fun accountOf(address: String) = accounts.first { it.address == address }

        override fun status() = status

        override fun importAccount(
            mnemonic: String,
            algorithm: KeyAlgorithm,
            addressIndex: UByte,
        ): Account {
            importError?.let { throw it }
            importedMnemonics += mnemonic
            importedAlgorithm = algorithm
            importedIndexes += addressIndex
            return accountAt(addressIndex)
        }

        override fun deriveAccount(
            address: String,
            addressIndex: UByte,
        ): Account {
            derived += address to addressIndex
            return accountAt(addressIndex)
        }

        override fun listAccounts() =
            accounts.map {
                ListedAccount(
                    it,
                    if (it.address ==
                        HUMAN_ADDRESS
                    ) {
                        HUMAN_PUBLIC_KEY
                    } else {
                        AUTOSIGN_PUBLIC_KEY
                    },
                )
            }

        override fun revealMnemonic(address: String) = mnemonic

        override fun deleteAccount(address: String): Account {
            deleteError?.let { throw it }
            deleted += address
            return accountOf(address)
        }

        override fun signTransaction(
            address: String,
            addressIndex: UByte,
            txn: ByteArray,
        ): SignedTransaction {
            signedAddress = address
            signedIndex = addressIndex
            return SignedTransaction(KeyAlgorithm.FALCON1024, txn, ByteArray(0))
        }

        override fun signArbitraryData(
            address: String,
            addressIndex: UByte,
            data: ByteArray,
        ): SignedArbitraryData {
            signError?.let { throw it }
            signedAddress = address
            signedIndex = addressIndex
            return SignedArbitraryData(KeyAlgorithm.FALCON1024, data.reversedArray())
        }

        override fun createAccount(
            algorithm: KeyAlgorithm,
            addressIndex: UByte,
        ) = accountAt(addressIndex)

        override fun devicePath() = "/dev/trusty-ipc-dev0"

        override fun protocolVersion() = ProtocolVersion(1u, 0u)

        override fun serviceName() = "seed-vault"
    }

    private companion object {
        const val HUMAN_ADDRESS = "FALCONHUMANADDRESS"
        const val AUTOSIGN_ADDRESS = "FALCONAUTOSIGNADDRESS"
        val HUMAN_PUBLIC_KEY = byteArrayOf(1, 2, 3)
        val AUTOSIGN_PUBLIC_KEY = byteArrayOf(4, 5, 6)

        // Not our indexes on purpose, to show the vault asks the applet for its own
        const val HUMAN_SIGN_APPLET_INDEX: UByte = 7u
        const val AUTOSIGN_APPLET_INDEX: UByte = 8u
        val APPLET_INDEXES = mapOf(HUMAN_SIGN_ADDRESS_INDEX to HUMAN_SIGN_APPLET_INDEX, AUTOSIGN_ADDRESS_INDEX to AUTOSIGN_APPLET_INDEX)

        val STORED_ACCOUNTS =
            listOf(
                StoredAccount(HUMAN_SIGN_ADDRESS_INDEX, HUMAN_ADDRESS, salt = 0, HUMAN_PUBLIC_KEY, encryptedPrivateKey = ByteArray(0)),
                StoredAccount(AUTOSIGN_ADDRESS_INDEX, AUTOSIGN_ADDRESS, salt = 0, AUTOSIGN_PUBLIC_KEY, encryptedPrivateKey = ByteArray(0)),
            )
    }
}
