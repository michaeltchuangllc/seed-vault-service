package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.model.AUTOSIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.model.HUMAN_SIGN_ADDRESS_INDEX
import com.michaeltchuang.seedvault.service.domain.vault.SeedKeyVault
import com.michaeltchuang.seedvault.service.domain.vault.SeedStorage
import com.michaeltchuang.seedvault.service.domain.vault.StoredAccount
import com.michaeltchuang.seedvault.service.domain.vault.StoredSeed
import uniffi.seed_vault_ffi.AppletErrorCode
import uniffi.seed_vault_ffi.KeyAlgorithm
import uniffi.seed_vault_ffi.ListedAccount
import uniffi.seed_vault_ffi.SeedVaultClient
import uniffi.seed_vault_ffi.SeedVaultClientInterface
import uniffi.seed_vault_ffi.SeedVaultException
import uniffi.seed_vault_ffi.SeedVaultStatus
import uniffi.seed_vault_ffi.autosignIndex
import uniffi.seed_vault_ffi.humanSignIndex

/**
 * Keeps Falcon-1024 keys in the Trusty applet in the TEE, through the algokit seed vault client.
 * The private keys never leave the TEE, so the database only holds the accounts and their public
 * keys. It is only available on devices that have the applet and let this app talk to it.
 *
 * The applet keeps one account per seed and address index, each with its own key pair: the human sign
 * index needs the user to approve each signature in the TEE, the autosign index does not. The seed is
 * imported once, at the human sign index, which is the account other wallets restore from the 25
 * words. The autosign account is then added with `deriveAccount`, a separate key derived by the
 * applet from the same seed. Each account keeps the public key the applet reports for it.
 *
 * @param clientFactory creates the client lazily, it loads a native library
 * @param appletIndexProvider the applet's address index for one of ours, read lazily for the same reason
 * @param humanAddressOf the human sign address of a seed phrase, to check the applet made the same one
 */
internal class TrustySeedKeyVault(
    clientFactory: () -> SeedVaultClientInterface = { SeedVaultClient() },
    private val appletIndexProvider: (Int) -> UByte = ::appletIndex,
    private val humanAddressOf: (List<String>) -> String = ::deriveHumanAddress,
) : SeedKeyVault {
    override val storage = SeedStorage.TRUSTY

    private val client by lazy(clientFactory)

    /**
     * Any failure counts as unavailable: no applet, no permission to reach it, an incompatible
     * version, or the native library not loading on this device.
     */
    override suspend fun isAvailable(): Boolean = runCatching { client.status() is SeedVaultStatus.Available }.getOrDefault(false)

    override suspend fun addSeed(seedPhrase: List<String>): StoredSeed {
        val humanAddress = humanAddressOf(seedPhrase)
        try {
            val imported =
                client.importAccount(seedPhrase.joinToString(" "), KeyAlgorithm.FALCON1024, appletIndexProvider(HUMAN_SIGN_ADDRESS_INDEX))
            check(imported.address == humanAddress) { "The Trusty applet made another address than the seed phrase has" }
        } catch (e: SeedVaultException.Applet) {
            // The TEE keeps its accounts when the app's data is cleared, so the phrase may be there already
            if (e.code != AppletErrorCode.ACCOUNT_ALREADY_EXISTS) throw e
        }
        // Adds the autosign key, or returns it when the seed already has it. Needs applet protocol 1.2
        val autosign = client.deriveAccount(humanAddress, appletIndexProvider(AUTOSIGN_ADDRESS_INDEX))
        val accounts =
            listOf(HUMAN_SIGN_ADDRESS_INDEX to humanAddress, AUTOSIGN_ADDRESS_INDEX to autosign.address).map { (index, address) ->
                StoredAccount(
                    index = index,
                    address = address,
                    // The applet adds the salt when it signs
                    salt = 0,
                    publicKey = listAccount(address).publicKey,
                    encryptedPrivateKey = ByteArray(0),
                )
            }
        return StoredSeed(encryptedEntropy = ByteArray(0), accounts = accounts)
    }

    override suspend fun revealSeedPhrase(seed: StoredSeed): List<String> =
        client.revealMnemonic(seed.account(HUMAN_SIGN_ADDRESS_INDEX).address).split(" ")

    override suspend fun signBytes(
        seed: StoredSeed,
        addressIndex: Int,
        data: ByteArray,
    ): ByteArray? {
        val listed = listAccount(seed.account(addressIndex).address)
        return try {
            client.signArbitraryData(listed.account.address, listed.account.addressIndex.toUByte(), data).signature
        } catch (e: SeedVaultException.Applet) {
            // The applet keeps data that looks like a transaction from being signed as arbitrary data
            if (e.code == AppletErrorCode.RESERVED_DOMAIN_PREFIX) {
                throw IllegalArgumentException("The data starts with a prefix the Trusty applet reserves", e)
            }
            throw e
        }
    }

    override suspend fun signTransaction(
        seed: StoredSeed,
        addressIndex: Int,
        transaction: ByteArray,
    ): ByteArray? {
        val listed = listAccount(seed.account(addressIndex).address)
        return client.signTransaction(listed.account.address, listed.account.addressIndex.toUByte(), transaction).signedTxn
    }

    /** Removes the seed, which takes its autosign account along. One that is already gone is skipped, so a failed delete can be tried again. */
    override suspend fun deleteSeed(seed: StoredSeed) {
        try {
            client.deleteAccount(seed.account(HUMAN_SIGN_ADDRESS_INDEX).address)
        } catch (e: SeedVaultException.Applet) {
            // Already gone, which is what was asked for
            if (e.code != AppletErrorCode.ACCOUNT_NOT_FOUND) throw e
        }
    }

    private fun listAccount(address: String): ListedAccount =
        client.listAccounts().firstOrNull { it.account.address == address }
            ?: throw IllegalStateException("The Trusty applet has no account $address")

    private companion object {
        fun appletIndex(index: Int): UByte =
            when (index) {
                HUMAN_SIGN_ADDRESS_INDEX -> humanSignIndex()
                AUTOSIGN_ADDRESS_INDEX -> autosignIndex()
                else -> throw IllegalArgumentException("The Trusty applet has no address index $index")
            }

        fun deriveHumanAddress(seedPhrase: List<String>): String {
            val entropy = Falcon1024.entropyFromSeedPhrase(seedPhrase)
            try {
                val account = Falcon1024.accountFromEntropy(entropy)
                account.privateKey.fill(0)
                return Falcon1024.account(HUMAN_SIGN_ADDRESS_INDEX, account.publicKey, encryptedPrivateKey = ByteArray(0)).address
            } finally {
                entropy.fill(0)
            }
        }
    }
}
