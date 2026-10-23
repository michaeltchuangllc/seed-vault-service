package com.michaeltchuang.seedvault.service;

/**
 * AIDL interface of the Seed Vault Service. Client apps bind to the service with the
 * SEED_VAULT_SERVICE action and need the ACCESS_SEED_VAULT permission.
 *
 * A seed can only be used by an app the user has authorized for it. Apps get authorized by
 * launching the AUTHORIZE_APP activity with startActivityForResult. Seeds are never returned,
 * only their public data and signatures.
 *
 * Every signature needs the user. Before calling a sign method, start the CONFIRM_SIGN activity with
 * startActivityForResult and the values you are about to sign (see SeedVaultContract). The user
 * sees them, verifies themselves (fingerprint or screen lock) and approves. After RESULT_OK, call
 * the sign method with the same values within a minute. An approval works once, for one call.
 *
 * Keep the seed-vault-sdk and seed-vault-service copies in sync. Client apps depend on SeedVaultSDK.
 */
interface ISeedVaultService {

    /** Check if the service is ready to handle requests. */
    boolean isServiceReady();

    /**
     * Get the seeds the calling app is authorized to use.
     * Returns: JSON array of {id, name, address, publicKey} where publicKey is Base64 encoded
     * and address is the Falcon-1024 account address.
     */
    String getAuthorizedSeeds();

    /**
     * Sign a byte array with the Falcon-1024 private key of a seed.
     * @param address address of a seed returned by getAuthorizedSeeds, the account that signs
     * @param data the bytes to sign
     * @return the signature, or null if signing failed
     * @throws SecurityException if the calling app is not authorized for the address, or there is no matching user approval
     * @throws IllegalArgumentException if the vault refuses the data, e.g. it looks like a transaction
     */
    byte[] signBytes(String address, in byte[] data);

    /**
     * Sign an unsigned Algorand transaction with the Falcon-1024 key of a seed. The user is shown what
     * the transaction does (type, receiver, amount, fee, and a warning for a rekey or close-out) on the
     * confirm sign screen, so pass exactly the bytes you started it with.
     * @param address address of a seed returned by getAuthorizedSeeds, the account that signs. It must
     * be the sender of the transaction
     * @param transaction the msgpack encoded unsigned transaction, without the "TX" prefix
     * @return the signed transaction, ready to send to algod, or null if signing failed
     * @throws SecurityException if the calling app is not authorized for the address, or there is no matching user approval
     * @throws IllegalArgumentException if the bytes are not a transaction, or its sender is not the address
     */
    byte[] signTransaction(String address, in byte[] transaction);
}
