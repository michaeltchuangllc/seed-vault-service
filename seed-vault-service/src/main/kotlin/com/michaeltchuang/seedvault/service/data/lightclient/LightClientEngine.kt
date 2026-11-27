package com.michaeltchuang.seedvault.service.data.lightclient

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.seed_vault_ffi.LightClientErrorCode
import uniffi.seed_vault_ffi.SeedProvider
import uniffi.seed_vault_ffi.SeedVaultClient
import uniffi.seed_vault_ffi.SeedVaultClientInterface
import uniffi.seed_vault_ffi.SeedVaultException
import uniffi.seed_vault_ffi.SeedVaultStatus
import uniffi.seed_vault_ffi.StateProofMessage
import uniffi.seed_vault_ffi.devPublisherPublicKey
import uniffi.seed_vault_ffi.fileWasmStorage
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** What the light client program has verified so far. All rounds are Algorand rounds. */
internal data class LightClientProgress(
    /** The program runs in this app's process, not in the Trusty applet, so there is no TEE behind it. */
    val emulated: Boolean,
    /** First round of the oldest interval the program can still verify transactions in. */
    val earliestRound: Long,
    /** First round of the next interval, which the next state proof attests. */
    val nextRound: Long,
    val capacity: Long,
    val lnProvenWeight: Long,
    val votersCommitment: ByteArray,
) {
    /** The last round that is verified: everything before [nextRound]. */
    val verifiedUpToRound: Long get() = nextRound - 1
}

/**
 * The program counts intervals from 0 (`earliestInterval`, `nextInterval` in its status), the first one starting
 * at `firstAttestedRound`. This is the Algorand round an interval starts at.
 */
internal fun intervalStartRound(
    firstAttestedRound: Long,
    intervalSize: Long,
    interval: Long,
): Long = firstAttestedRound + interval * intervalSize

/** A state proof message as algod serves it, with the proof itself sent next to it. */
internal class StateProofInput(
    val blockHeadersCommitment: ByteArray,
    val votersCommitment: ByteArray,
    val lnProvenWeight: Long,
    val firstAttestedRound: Long,
    val lastAttestedRound: Long,
)

/**
 * Runs the light client WASM program in the Trusty applet: installs it (signed), starts it from the anchor
 * state proof of a network, and advances it with the state proofs apps send. The program verifies every
 * proof, so an app that sends a wrong one only gets an error.
 *
 * The program id is the SHA-256 of the module and a program keeps one state, so every network has its own
 * module: the same code with a different `network` custom section, signed separately. That lets TestNet and
 * MainNet be followed at the same time with the same keys.
 *
 * @param clientFactory creates the client lazily, it loads a native library
 */
internal class LightClientEngine(
    val network: LightClientNetwork,
    clientFactory: () -> SeedVaultClientInterface = { SeedVaultClient() },
    private val module: () -> ByteArray,
    private val signedManifest: () -> ByteArray,
) {
    private val client by lazy(clientFactory)

    /** Two apps can call at once, the program takes one command at a time. */
    private val lock = Mutex()
    private val moduleBytes by lazy(module)
    private val programId by lazy { MessageDigest.getInstance("SHA-256").digest(moduleBytes) }

    /** Null while the program is not installed or not initialized. */
    suspend fun progress(): LightClientProgress? = withContext(Dispatchers.IO) { readProgress() }

    /** Installs the program if needed and starts it from the anchor of [network]. Does nothing if it already runs. */
    suspend fun start(): LightClientProgress = lock.withLock { withContext(Dispatchers.IO) { startLocked() } }

    /** Removes the program and the state it verified, then starts it again from the anchor. */
    suspend fun reset(): LightClientProgress =
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (isInstalled()) client.wasmUnload(programId)
                startLocked()
            }
        }

    private fun startLocked(): LightClientProgress {
        if (!isInstalled()) client.wasmInstall(moduleBytes, signedManifest())
        readProgress()?.let { return it }
        client.lightClientInit(
            programId,
            network.firstAttestedRound.toULong(),
            LightClientNetwork.INTERVAL_SIZE.toULong(),
            LightClientNetwork.CAPACITY.toULong(),
            Base64.getDecoder().decode(network.anchorVotersCommitment),
            network.anchorLnProvenWeight.toULong(),
        )
        return checkNotNull(readProgress()) { "The light client did not start" }
    }

    /**
     * Has the program verify [proof] for the next interval, which [message] must attest. Throws if the proof
     * is not for the next interval or the program does not accept it.
     */
    suspend fun advance(
        message: StateProofInput,
        proof: ByteArray,
    ): LightClientProgress =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val current = checkNotNull(readProgress()) { "The light client is not started" }
                val lastRound = current.nextRound + LightClientNetwork.INTERVAL_SIZE - 1
                require(message.firstAttestedRound == current.nextRound && message.lastAttestedRound == lastRound) {
                    "The state proof is for rounds ${message.firstAttestedRound}..${message.lastAttestedRound}, " +
                        "the next one is ${current.nextRound}..$lastRound"
                }
                client.lightClientAdvance(
                    programId,
                    StateProofMessage(
                        message.blockHeadersCommitment,
                        message.votersCommitment,
                        message.lnProvenWeight.toULong(),
                        message.firstAttestedRound.toULong(),
                        message.lastAttestedRound.toULong(),
                    ),
                    proof,
                )
                checkNotNull(readProgress())
            }
        }

    private fun isInstalled(): Boolean {
        requireApplet()
        return client.wasmListPrograms().any { it.programId.contentEquals(programId) }
    }

    /** Fails with the reason when the applet cannot be used, for a client that is not emulated. */
    private fun requireApplet() {
        // The emulated runtime lives in this process, there is no applet to probe
        if (client.isEmulated()) return
        val reason =
            when (val status = client.status()) {
                is SeedVaultStatus.Available -> return
                is SeedVaultStatus.PermissionDenied -> "this app may not reach it (${status.reason})"
                is SeedVaultStatus.IncompatibleVersion -> "its version is not compatible"
                is SeedVaultStatus.NotSupported -> "this device has none"
                is SeedVaultStatus.ServiceNotFound -> "its service was not found"
            }
        throw IllegalStateException("The Trusty applet is not available: $reason")
    }

    private fun readProgress(): LightClientProgress? {
        if (!isInstalled()) return null
        return try {
            client.lightClientStatus(programId)?.let {
                LightClientProgress(
                    emulated = client.isEmulated(),
                    earliestRound =
                        intervalStartRound(
                            it.firstAttestedRound.toLong(),
                            it.intervalSize.toLong(),
                            it.earliestInterval.toLong(),
                        ),
                    nextRound = intervalStartRound(it.firstAttestedRound.toLong(), it.intervalSize.toLong(), it.nextInterval.toLong()),
                    capacity = it.capacity.toLong(),
                    lnProvenWeight = it.lnProvenWeight.toLong(),
                    votersCommitment = it.votersCommitment,
                )
            }
        } catch (e: SeedVaultException.LightClient) {
            if (e.code is LightClientErrorCode.NotInitialized) null else throw e
        }
    }
}

/**
 * The client the light clients run on: the Trusty applet when this device has one, otherwise the same runtime
 * in this app's process ([SeedVaultClient.emulated]), which behaves the same but is protected by the Android
 * sandbox and not a TEE. The light client holds no keys, only public chain data, so it needs no seeds and
 * trusts only the publisher key its modules are signed with.
 */
internal fun lightClientClient(context: Context): SeedVaultClientInterface {
    val trusty = SeedVaultClient()
    if (runCatching { trusty.status() is SeedVaultStatus.Available }.getOrDefault(false)) {
        // Verifying a MainNet state proof takes a few seconds in the TEE, longer than the 10 s default reply timeout allows for
        return SeedVaultClient.withConfig(trusty.devicePath(), trusty.serviceName(), TRUSTY_REPLY_TIMEOUT_MS)
    }
    return SeedVaultClient.emulated(
        storage = fileWasmStorage(File(context.filesDir, "light_client_wasm").path),
        seeds = NoSeeds,
        publisherKeys = listOf(devPublisherPublicKey()),
        allowUnsigned = false,
    )
}

/** How long to wait for the applet to answer one request, in milliseconds. */
private const val TRUSTY_REPLY_TIMEOUT_MS = 60_000u

/** The light client program needs no key, so the emulated runtime has no seeds to give. */
private object NoSeeds : SeedProvider {
    override fun seedEntropy(address: String): ByteArray? = null

    override fun seedAddresses(): List<String> = emptyList()
}

/** One engine per network, with the signed module for it from the app's assets. They share one client. */
internal fun lightClientEngines(context: Context): List<LightClientEngine> {
    val client by lazy { lightClientClient(context) }
    return LightClientNetwork.entries.map { network ->
        val name = network.name.lowercase()
        LightClientEngine(
            network = network,
            clientFactory = { client },
            module = { context.assets.open("lightclient/light_client_$name.wasm").use { it.readBytes() } },
            signedManifest = { context.assets.open("lightclient/light_client_$name.svwm").use { it.readBytes() } },
        )
    }
}
