package com.michaeltchuang.seedvault.service.domain.vault

/** The Falcon-1024 operations [RoomSeedKeyVault] needs. [NativeFalconKeys] is the real one, a test can use a fake. */
internal interface FalconKeys {
    fun entropyFromSeedPhrase(seedPhrase: List<String>): ByteArray

    fun seedPhraseFromEntropy(entropy: ByteArray): List<String>

    /** The key pair of the account at [addressIndex] of the seed with [entropy]. Every index has its own. */
    fun keyPairAt(
        entropy: ByteArray,
        addressIndex: Int,
    ): FalconKeyPair

    /** The account at [addressIndex] for its key pair's [publicKey], with no private key stored. */
    fun account(
        addressIndex: Int,
        publicKey: ByteArray,
    ): StoredAccount

    fun signBytes(
        privateKey: ByteArray,
        data: ByteArray,
    ): ByteArray

    fun signTransaction(
        publicKey: ByteArray,
        privateKey: ByteArray,
        salt: Int,
        transaction: ByteArray,
    ): ByteArray
}

internal class FalconKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
)
