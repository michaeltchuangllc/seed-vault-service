package com.michaeltchuang.seedvault.service.domain.usecase

import com.michaeltchuang.seedvault.service.data.vault.Falcon1024
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GenerateSeedPhraseUseCase(
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GenerateSeedPhrase {
    override suspend fun invoke(): List<String> =
        withContext(coroutineDispatcher) {
            // A 25-word Algorand mnemonic encodes 32 bytes of entropy
            val entropy = Falcon1024.randomEntropy()
            try {
                Falcon1024.seedPhraseFromEntropy(entropy)
            } finally {
                entropy.fill(0)
            }
        }
}
