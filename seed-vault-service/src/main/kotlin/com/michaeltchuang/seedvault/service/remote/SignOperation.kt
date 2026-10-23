package com.michaeltchuang.seedvault.service.remote

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Exactly what a signature is for. The user approves one of these on the confirm sign screen and
 * the service only signs when the same operation comes in over AIDL, so an app cannot get
 * something signed that the user was not shown. Equal when the signing address and every signed value match.
 */
internal class SignOperation private constructor(
    val address: String,
    private val digest: String,
) {
    override fun equals(other: Any?) = other is SignOperation && other.address == address && other.digest == digest

    override fun hashCode() = 31 * address.hashCode() + digest.hashCode()

    companion object {
        fun bytes(
            address: String,
            data: ByteArray,
        ) = SignOperation(address, digestOf("bytes".toByteArray(), address.toByteArray(), data))

        fun transaction(
            address: String,
            transaction: ByteArray,
        ) = SignOperation(address, digestOf("transaction".toByteArray(), address.toByteArray(), transaction))

        /** Each part is length-prefixed, so different splits of the same bytes give different digests. */
        private fun digestOf(vararg parts: ByteArray): String {
            val sha = MessageDigest.getInstance("SHA-256")
            parts.forEach {
                sha.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it.size).array())
                sha.update(it)
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
