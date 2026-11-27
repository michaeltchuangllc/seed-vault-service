package com.michaeltchuang.seedvault.demo.lightclient

import android.content.Context
import androidx.core.content.edit
import com.michaeltchuang.seedvault.sdk.domain.model.LightClientNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the last check of one network found, or why it failed. */
data class LightClientCheck(
    val time: Long,
    val message: String,
)

/** The last check of each network, kept so the screen shows it whenever the background job ran. */
class LightClientLog private constructor(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("light_client_checks", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val checks: StateFlow<Map<LightClientNetwork, LightClientCheck>> = state.asStateFlow()

    fun record(
        network: LightClientNetwork,
        check: LightClientCheck,
    ) {
        preferences.edit {
            putLong("${network.name}_time", check.time)
            putString("${network.name}_message", check.message)
        }
        state.value = state.value + (network to check)
    }

    private fun read(): Map<LightClientNetwork, LightClientCheck> =
        LightClientNetwork.entries
            .mapNotNull { network ->
                val message = preferences.getString("${network.name}_message", null) ?: return@mapNotNull null
                network to LightClientCheck(preferences.getLong("${network.name}_time", 0), message)
            }.toMap()

    companion object {
        @Volatile private var instance: LightClientLog? = null

        fun get(context: Context): LightClientLog =
            instance ?: synchronized(this) {
                instance ?: LightClientLog(context.applicationContext).also { instance = it }
            }
    }
}
