package com.michaeltchuang.seedvault.sdk.client

import com.michaeltchuang.seedvault.sdk.domain.model.AlgorandPaymentParams
import uniffi.algokit_composer_ffi.CommonTxnParams
import uniffi.algokit_composer_ffi.ComposerParams
import uniffi.algokit_composer_ffi.PaymentParams
import uniffi.algokit_composer_ffi.SuggestedParams
import uniffi.algokit_composer_ffi.TxnParams
import uniffi.algokit_composer_ffi.TxnParamsKind
import uniffi.algokit_composer_ffi.compose

/**
 * Builds unsigned Algorand transactions to pass to `SeedVaultServiceClient.signTransaction`. The
 * vault signs whatever transaction the user approves, so any transaction can be built elsewhere.
 */
object AlgorandTransactions {
    /** A Falcon-1024 account needs more than the minimum fee. */
    const val FALCON_FEE_MICRO_ALGOS = 3000L

    /** An unsigned payment of [amountMicroAlgos] from [sender] to [receiverAddress]. */
    fun payment(
        sender: String,
        receiverAddress: String,
        amountMicroAlgos: Long,
        params: AlgorandPaymentParams,
        feeMicroAlgos: Long = FALCON_FEE_MICRO_ALGOS,
    ): ByteArray {
        require(amountMicroAlgos >= 0) { "Amount cannot be negative" }
        return compose(
            txnParams =
                listOf(
                    TxnParams(
                        kind = TxnParamsKind.PAYMENT,
                        payment =
                            PaymentParams(
                                common = CommonTxnParams(sender = sender, note = null, staticFee = feeMicroAlgos.toULong()),
                                receiver = receiverAddress,
                                amount = amountMicroAlgos.toULong(),
                                closeRemainderTo = null,
                            ),
                    ),
                ),
            composerParams =
                ComposerParams(
                    suggestedParams =
                        SuggestedParams(
                            fee = feeMicroAlgos.toULong(),
                            flatFee = true,
                            firstRoundValid = params.firstValid.toULong(),
                            lastRoundValid = params.lastValid.toULong(),
                            genesisHash = params.genesisHash,
                            genesisId = params.genesisId,
                        ),
                ),
        ).single()
    }
}
