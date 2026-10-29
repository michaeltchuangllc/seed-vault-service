package com.michaeltchuang.seedvault.service.domain.vault

/** Encrypts the secrets that [RoomSeedKeyVault] keeps in the database. */
internal interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(encrypted: ByteArray): ByteArray
}
