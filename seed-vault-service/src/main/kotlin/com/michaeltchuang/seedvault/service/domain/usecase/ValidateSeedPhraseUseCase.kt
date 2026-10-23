package com.michaeltchuang.seedvault.service.domain.usecase

import com.michaeltchuang.seedvault.service.data.vault.Falcon1024
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ValidateSeedPhraseUseCase(
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ValidateSeedPhrase {
    override suspend fun invoke(words: List<String>): Boolean =
        withContext(coroutineDispatcher) {
            if (words.size != SEED_PHRASE_WORD_COUNT) return@withContext false
            // Decoding fails on unknown words and on a wrong checksum word
            runCatching { Falcon1024.entropyFromSeedPhrase(words) }
                .onSuccess { entropy -> entropy.fill(0) }
                .isSuccess
        }
}

internal const val SEED_PHRASE_WORD_COUNT = 25
