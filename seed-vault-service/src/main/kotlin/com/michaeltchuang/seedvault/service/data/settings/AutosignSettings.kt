package com.michaeltchuang.seedvault.service.data.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether apps may sign with the autosign address of a seed (address index 1) without the user
 * approving each signature. The human sign address (index 0) always needs the user. On by default, since most users would never look for it; they can turn it
 * off on the Security screen.
 */
internal interface AutosignSettings {
    val isEnabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)
}

/** [AutosignSettings] in the app's private shared preferences. */
internal class SharedPreferencesAutosignSettings(
    context: Context,
) : AutosignSettings {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))

    override val isEnabled: StateFlow<Boolean> = enabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        // Committed before the change is visible, so the service never acts on a value that is not stored
        preferences.edit(commit = true) { putBoolean(KEY_ENABLED, enabled) }
        this.enabled.value = enabled
    }

    private companion object {
        const val PREFERENCES_NAME = "seed_vault_settings"
        const val KEY_ENABLED = "autosign_enabled"
        const val DEFAULT_ENABLED = true
    }
}
