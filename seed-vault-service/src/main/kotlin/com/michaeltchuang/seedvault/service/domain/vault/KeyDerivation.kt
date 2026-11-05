package com.michaeltchuang.seedvault.service.domain.vault

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives secrets for an app from a seed, e.g. the key of a passkey, without ever handing out the
 * entropy or a signing key.
 *
 * The account signs a fixed message with its own key, and the secret comes from HKDF-SHA512
 * (RFC 5869) over that signature. This only works because Falcon-1024 signing here is deterministic
 * (go-algorand's `FalconSigner.SignBytes`): the same key and message always give the same signature,
 * so the same seed gives the same secret on any device and in either vault ([SeedStorage.ROOM] or
 * [SeedStorage.TRUSTY]). A signature scheme with randomized signing would not work.
 *
 * The message holds the account address, the [domain] and the caller's `info`, so a different value
 * for any of them gives an unrelated result. Each account of a seed signs with its own key (the human
 * sign key or an autosign key), so an autosign key never reveals what the human sign key derives. [VERSION] is part of the message and the salt: a change to this
 * algorithm must bump it, since it would otherwise move every key derived before.
 */
internal object KeyDerivation {
    /** Version of this derivation, reported to apps as `deriveKeyMaterialVersion`. */
    const val VERSION = 1

    /** Domains must start with this, so the derivation cannot be used for another purpose. */
    const val DOMAIN_PREFIX = "passkey/"

    const val MIN_LENGTH = 32
    const val MAX_LENGTH = 64
    const val MAX_DOMAIN_BYTES = 64
    const val MAX_INFO_BYTES = 1024

    private const val HMAC = "HmacSHA512"
    private val SALT = "algokit-seedvault-derive-v$VERSION".encodeToByteArray()

    /**
     * Starts the signed message. It must not start with a signing-domain prefix the vault refuses
     * (`TX`, `TG`, `Program`, `ProgData`, `MX`), which "algokit..." does not.
     */
    private val MESSAGE_PREFIX = "algokit-seedvault-derive-v$VERSION".encodeToByteArray()

    /** Throws [IllegalArgumentException] for a request the vault refuses. */
    fun requireValid(
        domain: String,
        info: ByteArray,
        length: Int,
    ) {
        require(domain.startsWith(DOMAIN_PREFIX) && domain.length > DOMAIN_PREFIX.length) {
            "The domain must start with $DOMAIN_PREFIX"
        }
        require(domain.encodeToByteArray().size <= MAX_DOMAIN_BYTES) { "The domain is longer than $MAX_DOMAIN_BYTES bytes" }
        require(info.size <= MAX_INFO_BYTES) { "The info is longer than $MAX_INFO_BYTES bytes" }
        require(length in MIN_LENGTH..MAX_LENGTH) { "The length must be $MIN_LENGTH to $MAX_LENGTH bytes" }
    }

    /**
     * The exact bytes the account at [address] signs for this request. Each part is length-prefixed, so
     * different splits of the same bytes give different messages.
     */
    fun message(
        address: String,
        domain: String,
        info: ByteArray,
    ): ByteArray {
        requireValid(domain, info, MIN_LENGTH)
        return MESSAGE_PREFIX + lengthPrefixed(domain.encodeToByteArray()) + lengthPrefixed(address.encodeToByteArray()) + info
    }

    /**
     * @param signature the account's own signature over [message] for the same arguments. Not changed
     * or kept; the caller wipes it
     */
    fun derive(
        signature: ByteArray,
        address: String,
        domain: String,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        requireValid(domain, info, length)
        require(signature.isNotEmpty()) { "The signature is empty" }
        return hkdf(ikm = signature, salt = SALT, info = message(address, domain, info), length = length, algorithm = HMAC)
    }

    /** RFC 5869 extract-and-expand. [algorithm] is a Mac name, other than [HMAC] only for tests against the RFC vectors. */
    internal fun hkdf(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
        algorithm: String,
    ): ByteArray {
        val extract = Mac.getInstance(algorithm)
        extract.init(SecretKeySpec(salt, algorithm))
        val prk = extract.doFinal(ikm)
        try {
            val expand = Mac.getInstance(algorithm)
            expand.init(SecretKeySpec(prk, algorithm))
            val out = ByteArray(length)
            var previous = ByteArray(0)
            var offset = 0
            var counter = 1
            while (offset < length) {
                expand.update(previous)
                expand.update(info)
                expand.update(counter.toByte())
                previous = expand.doFinal()
                val count = minOf(previous.size, length - offset)
                previous.copyInto(out, offset, 0, count)
                offset += count
                counter++
            }
            return out
        } finally {
            prk.fill(0)
        }
    }

    /** Each part is length-prefixed, so different splits of the same bytes give different results. */
    private fun lengthPrefixed(part: ByteArray): ByteArray =
        ByteBuffer
            .allocate(Int.SIZE_BYTES + part.size)
            .putInt(part.size)
            .put(part)
            .array()
}
