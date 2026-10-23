package com.michaeltchuang.seedvault.service.remote

import uniffi.algokit_transact_ffi.TransactionType
import uniffi.algokit_transact_ffi.decodeTransaction

/** What the user is shown for an Algorand transaction before approving it. Amounts are in base units. */
internal class DecodedTransaction(
    val type: String,
    val sender: String,
    val feeMicroAlgos: Long,
    val receiver: String? = null,
    val amount: Long? = null,
    val assetId: Long? = null,
    val rekeyTo: String? = null,
    val closeRemainderTo: String? = null,
) {
    val isAlgoPayment get() = type == PAYMENT && assetId == null

    companion object {
        const val PAYMENT = "payment"
    }
}

/** Reads the unsigned transaction a client wants signed. Returns null when the bytes are not a transaction. */
internal fun interface TransactionDecoder {
    fun decode(transaction: ByteArray): DecodedTransaction?
}

internal object AlgokitTransactionDecoder : TransactionDecoder {
    override fun decode(transaction: ByteArray): DecodedTransaction? =
        runCatching {
            val decoded = decodeTransaction(transaction)
            val payment = decoded.payment
            val assetTransfer = decoded.assetTransfer
            DecodedTransaction(
                type = decoded.transactionType.toLabel(),
                sender = decoded.sender,
                feeMicroAlgos = (decoded.fee ?: 0uL).toLong(),
                receiver = payment?.receiver ?: assetTransfer?.receiver,
                amount = payment?.amount?.toLong() ?: assetTransfer?.amount?.toLong(),
                assetId = assetTransfer?.assetId?.toLong(),
                rekeyTo = decoded.rekeyTo,
                closeRemainderTo = payment?.closeRemainderTo ?: assetTransfer?.closeRemainderTo,
            )
        }.getOrNull()

    private fun TransactionType.toLabel() =
        when (this) {
            TransactionType.PAYMENT -> DecodedTransaction.PAYMENT
            TransactionType.ASSET_TRANSFER -> "asset transfer"
            TransactionType.ASSET_FREEZE -> "asset freeze"
            TransactionType.ASSET_CONFIG -> "asset config"
            TransactionType.KEY_REGISTRATION -> "key registration"
            TransactionType.APP_CALL -> "app call"
            TransactionType.HEARTBEAT -> "heartbeat"
            TransactionType.STATE_PROOF -> "state proof"
        }
}
