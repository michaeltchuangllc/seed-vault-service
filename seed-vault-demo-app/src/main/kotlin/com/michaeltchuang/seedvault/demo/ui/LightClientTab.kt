package com.michaeltchuang.seedvault.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.michaeltchuang.seedvault.demo.ui.viewmodel.LightClientNetworkState
import com.michaeltchuang.seedvault.demo.ui.viewmodel.LightClientViewModel
import java.text.DateFormat
import java.util.Date

/**
 * The light client in the Seed Vault app, on TestNet and MainNet. This app fetches the state proofs from algod
 * and Seed Vault Service has its Trusty applet verify them, so the vault itself needs no network access.
 * A background job checks about every 15 minutes (a state proof is made every 256 rounds, about 12 minutes).
 */
@Composable
internal fun ColumnScope.LightClientTab(viewModel: LightClientViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Text(
        text =
            "Algorand light client (not Solana). Seed Vault follows Algorand TestNet and MainNet in its " +
                "Trusty applet. This app fetches each state proof from algod and the applet verifies it. " +
                "A background job checks about every 15 minutes.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    uiState.networks.forEach { NetworkCard(it, busy = uiState.busy, onReset = { viewModel.reset(it.network) }) }
    Button(
        onClick = viewModel::verifyNow,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (uiState.busy) "Working..." else "Verify latest state proofs now")
    }
}

private const val EMULATED = "Runs in the Seed Vault app (no Trusty applet on this device)"
private const val TRUSTY = "Runs in the Trusty applet"

@Composable
private fun NetworkCard(
    network: LightClientNetworkState,
    busy: Boolean,
    onReset: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Algorand ${network.network.label}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val status = network.status
            Text(
                text =
                    when {
                        status == null && network.error != null -> "Unavailable: ${network.error}"
                        status == null -> "Looking at Seed Vault..."
                        !status.started -> "Not started. It starts from the state proof at round ${status.anchorRound}."
                        else ->
                            "${if (status.emulated) EMULATED else TRUSTY}\n" +
                                "Verified up to round ${status.verifiedUpToRound}\n" +
                                "Oldest interval kept: round ${status.earliestRound} (capacity ${status.capacity})\n" +
                                "Next state proof covers rounds ${status.nextRound} to ${status.nextRound + status.intervalSize - 1}"
                    },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            network.lastCheck?.let {
                Text(
                    text = "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it.time))}\n${it.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            OutlinedButton(onClick = onReset, enabled = !busy, modifier = Modifier.padding(top = 12.dp)) { Text("Reset") }
        }
    }
}
