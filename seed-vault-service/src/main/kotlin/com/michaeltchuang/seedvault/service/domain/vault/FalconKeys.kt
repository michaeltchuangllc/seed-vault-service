package com.michaeltchuang.seedvault.service.domain.vault

/** The Falcon-1024 operations [RoomSeedKeyVault] needs. [NativeFalconKeys] is the real one, a test can use a fake. */
internal interface FalconKeys {
    fun entropyFromSeedPhrase(seedPhrase: List<String>): ByteArray

    fun seedPhraseFromEntropy(entropy: ByteArray): List<String>

    /** The key pair behind [entropy]. */
    fun keyPairFromEntropy(entropy: ByteArray): FalconKeyPair

    /** An account at every address index for [publicKey], with no private key stored. */
    fun accounts(publicKey: ByteArray): List<StoredAccount>

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
