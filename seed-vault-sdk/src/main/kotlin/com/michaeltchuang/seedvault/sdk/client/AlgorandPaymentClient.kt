package com.michaeltchuang.seedvault.sdk.client

import android.content.Intent
import com.michaeltchuang.seedvault.sdk.data.repository.AlgorandRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An unsigned payment, reused for both user approval and signing. */
class PreparedAlgorandPayment internal constructor(
    internal val senderAddress: String,
    val receiverAddress: String,
    val amountMicroAlgos: Long,
    internal val transaction: ByteArray,
    private val client: SeedVaultServiceClient,
) {
    val confirmationIntent: Intent
        get() = client.confirmSignTransactionIntent(senderAddress, transaction)
}

/** Builds payments and submits them after the host receives successful user approval. */
class AlgorandPaymentClient(
    private val vault: SeedVaultServiceClient,
    private val repository: AlgorandRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun preparePayment(
        senderAddress: String,
        receiverAddress: String,
        amountMicroAlgos: Long,
    ): PreparedAlgorandPayment {
        require(senderAddress.isNotBlank()) { "Sender address is required" }
        require(receiverAddress.isNotBlank()) { "Receiver address is required" }
        require(amountMicroAlgos >= 0) { "Amount cannot be negative" }
        val transaction =
            AlgorandTransactions.payment(senderAddress, receiverAddress, amountMicroAlgos, repository.getPaymentParams())
        return PreparedAlgorandPayment(senderAddress, receiverAddress, amountMicroAlgos, transaction, vault)
    }

    /** Call only after RESULT_OK. The service enforces exact, one-time approval of this transaction. */
    suspend fun signAndSend(payment: PreparedAlgorandPayment): String {
        val signed =
            withContext(ioDispatcher) {
                vault.signTransaction(payment.senderAddress, payment.transaction)
            } ?: error("Seed Vault Service could not sign the payment")
        return repository.sendSignedTransaction(signed)
    }
}
