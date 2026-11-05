package com.michaeltchuang.seedvault.service;

/**
 * AIDL interface of the Seed Vault Service. Client apps bind to the service with the
 * SEED_VAULT_SERVICE action and need the ACCESS_SEED_VAULT permission.
 *
 * A seed can only be used by an app the user has authorized for it. Apps get authorized by
 * launching the AUTHORIZE_APP activity with startActivityForResult. Seeds are never returned,
 * only their public data and signatures.
 *
 * Every seed has one or more Falcon-1024 accounts (see getAuthorizedSeeds), each with an index, an
 * address and a public key. Today that is index 0, the human sign account, and index 1, the autosign
 * account. Apps should not assume either count: loop over the accounts and read requiresApproval.
 *
 * A signature with the human sign account always needs the user. Before calling a sign method, start
 * the CONFIRM_SIGN activity with startActivityForResult and the values you are about to sign (see
 * SeedVaultContract). The user sees them, verifies themselves (fingerprint or screen lock) and
 * approves. After RESULT_OK, call the sign method with the same values within a minute. An approval
 * works once, for one call.
 *
 * A signature with the autosign account (isAutosign) needs no approval while the user allows
 * autosign in the Seed Vault security settings. While they do not, it needs one like the human sign
 * account. requiresApproval says which applies as of getAuthorizedSeeds; the service checks again
 * on every signature.
 *
 * Keep the seed-vault-sdk and seed-vault-service copies in sync. Client apps depend on SeedVaultSDK.
 */
interface ISeedVaultService {

    /** Check if the service is ready to handle requests. */
    boolean isServiceReady();

    /**
     * Get the seeds the calling app is authorized to use.
     * Returns: JSON array of {id, name, accounts} where accounts is an array, by index, of
     * {index, address, publicKey, isAutosign, requiresApproval}: publicKey is the Base64 encoded
     * Falcon-1024 public key of the account. isAutosign false and requiresApproval true may be left out.
     */
    String getAuthorizedSeeds();

    /**
     * Sign a byte array with the Falcon-1024 private key of a seed.
     * @param address the address of an account returned by getAuthorizedSeeds, the account that signs
     * @param data the bytes to sign
     * @return the signature, or null if signing failed
     * @throws SecurityException if the calling app is not authorized for the address, or the address needs a user approval and there is none
     * @throws IllegalArgumentException if the vault refuses the data, e.g. it looks like a transaction
     */
    byte[] signBytes(String address, in byte[] data);

    /**
     * Sign an unsigned Algorand transaction with the Falcon-1024 key of a seed. The user is shown what
     * the transaction does (type, receiver, amount, fee, and a warning for a rekey or close-out) on the
     * confirm sign screen, so pass exactly the bytes you started it with.
     * @param address the address of an account returned by getAuthorizedSeeds, the account that signs. It must
     * be the sender of the transaction
     * @param transaction the msgpack encoded unsigned transaction, without the "TX" prefix
     * @return the signed transaction, ready to send to algod, or null if signing failed
     * @throws SecurityException if the calling app is not authorized for the address, or the address needs a user approval and there is none
     * @throws IllegalArgumentException if the bytes are not a transaction, or its sender is not the address
     */
    byte[] signTransaction(String address, in byte[] transaction);

    /**
     * Derive key material for an app from the seed behind an account, e.g. the private key of a passkey.
     * The seed phrase and the signing keys are never returned. The result is the same every time for the
     * same seed, address, domain and info, so it can be recreated after the app or device is restored
     * from the same seed phrase. Added after signTransaction, so the transaction codes of the methods
     * above did not change; an older Seed Vault app does not have it (see supportsKeyDerivation in getAuthorizedSeeds).
     *
     * Needs an approval exactly like a signature with the same account (see requiresApproval in
     * getAuthorizedSeeds): always for the human sign account, and for an autosign account only while the user
     * does not allow autosign. When it needs one, start CONFIRM_SIGN with the same address, domain, info and
     * length first (see SeedVaultContract), then call this within a minute.
     * The account signs a fixed message (address, domain, info) with its own Falcon-1024 key, which is
     * deterministic, and the result is HKDF-SHA512 over that signature, version 1. It works the same for
     * seeds in Room and in the Trusty applet.
     *
     * @param address the address of an account returned by getAuthorizedSeeds
     * @param domain what the key is for. Must start with "passkey/" and be at most 64 bytes
     * @param info context from the app, e.g. origin and user handle of a passkey. At most 1024 bytes
     * @param length number of bytes to return, 32 to 64
     * @return the derived bytes, or null if the derivation failed
     * @throws SecurityException if the calling app is not authorized for the address, or there is no approval
     * @throws IllegalArgumentException if the domain, info or length is refused
     * @throws UnsupportedOperationException if the keys of the seed cannot derive
     */
    byte[] deriveKeyMaterial(String address, String domain, in byte[] info, int length);
}
