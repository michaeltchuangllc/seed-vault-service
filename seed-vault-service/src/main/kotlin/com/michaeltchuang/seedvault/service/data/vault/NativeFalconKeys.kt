package com.michaeltchuang.seedvault.service.data.vault

import com.michaeltchuang.seedvault.service.domain.vault.FalconKeyPair
import com.michaeltchuang.seedvault.service.domain.vault.FalconKeys

/** [FalconKeys] through the algokit-core Rust FFI. */
internal object NativeFalconKeys : FalconKeys {
    override fun entropyFromSeedPhrase(seedPhrase: List<String>) = Falcon1024.entropyFromSeedPhrase(seedPhrase)

    override fun seedPhraseFromEntropy(entropy: ByteArray) = Falcon1024.seedPhraseFromEntropy(entropy)

    override fun keyPairFromEntropy(entropy: ByteArray): FalconKeyPair {
        val account = Falcon1024.accountFromEntropy(entropy)
        return FalconKeyPair(account.publicKey, account.privateKey)
    }

    override fun accounts(publicKey: ByteArray) = Falcon1024.accounts(publicKey, encryptedPrivateKey = ByteArray(0))

    override fun signBytes(
        privateKey: ByteArray,
        data: ByteArray,
    ) = Falcon1024.signBytes(privateKey, data)

    override fun signTransaction(
        publicKey: ByteArray,
        privateKey: ByteArray,
        salt: Int,
        transaction: ByteArray,
    ) = Falcon1024.signTransaction(publicKey, privateKey, salt, transaction)
}
