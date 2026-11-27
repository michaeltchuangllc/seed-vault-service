package com.michaeltchuang.seedvault.demo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.michaeltchuang.seedvault.demo.lightclient.LightClientCheck
import com.michaeltchuang.seedvault.demo.lightclient.LightClientLog
import com.michaeltchuang.seedvault.demo.lightclient.LightClientRunner
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientNetwork
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LightClientNetworkState(
    val network: LightClientNetwork,
    val status: LightClientStatus? = null,
    /** Why the status could not be read, e.g. the Seed Vault app has no Trusty applet. */
    val error: String? = null,
    val lastCheck: LightClientCheck? = null,
)

data class LightClientUiState(
    val networks: List<LightClientNetworkState> = LightClientNetwork.entries.map { LightClientNetworkState(it) },
    val busy: Boolean = false,
)

/**
 * Shows the Seed Vault light client of each network. The background job (`LightClientWorker`) keeps them up to
 * date, so the last check shown is whenever that ran; "Verify now" runs one right away.
 */
class LightClientViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val runner = LightClientRunner(application)
    private val _uiState = MutableStateFlow(LightClientUiState())
    val uiState: StateFlow<LightClientUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The background job writes the log, so the progress is read again after it
            LightClientLog.get(application).checks.collect { checks ->
                val statuses = runner.status()
                _uiState.update { state ->
                    state.copy(
                        networks =
                            state.networks.map { item ->
                                val result = statuses.getValue(item.network)
                                item.copy(
                                    status = result.getOrNull(),
                                    error = result.exceptionOrNull()?.let { it.message ?: it::class.java.simpleName },
                                    lastCheck = checks[item.network],
                                )
                            },
                    )
                }
            }
        }
    }

    fun reset(network: LightClientNetwork) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                runner.reset(network)
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    fun verifyNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                runner.check()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
