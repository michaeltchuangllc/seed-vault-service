package com.michaeltchuang.seedvault.demo.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.michaeltchuang.seedvault.sdk.SeedVaultServiceConstants
import com.michaeltchuang.seedvault.sdk.client.SolanaSeedVaultClient

/**
 * Permission Gauntlet Activity that requests Seed Vault permission at runtime.
 * This ensures the app has ACCESS_SEED_VAULT permission before proceeding to MainActivity.
 */
class PermissionGauntletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The Seed Vault Service permission is dangerous: it is lost when that app is reinstalled, so ask
        // for it even when the Solana one is already granted. It is only defined while that app is
        // installed, and the connect button reports the problem if it is not. The Solana one is only
        // asked for when the Solana Seed Vault is installed: without it the permission does not exist
        // and the request is denied right away.
        val missing =
            buildList {
                if (SolanaSeedVaultClient.isAvailable(this@PermissionGauntletActivity) &&
                    !SolanaSeedVaultClient.hasPermission(this@PermissionGauntletActivity)
                ) {
                    add(SolanaSeedVaultClient.PERMISSION_ACCESS_SEED_VAULT)
                }
                if (!isGranted(SeedVaultServiceConstants.PERMISSION_ACCESS_SEED_VAULT)) {
                    add(SeedVaultServiceConstants.PERMISSION_ACCESS_SEED_VAULT)
                }
            }
        if (missing.isEmpty()) {
            continueToMain()
            return
        }
        requestPermissions(missing.toTypedArray(), 0)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Either vault's permission is enough to continue, the demo screen shows the state of each
        if (SolanaSeedVaultClient.hasPermission(this) ||
            isGranted(SeedVaultServiceConstants.PERMISSION_ACCESS_SEED_VAULT)
        ) {
            Log.d(TAG, "Access Seed Vault permission granted")
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            Log.e(TAG, "Access Seed Vault permission not granted")
        }
        finish()
    }

    private fun continueToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val TAG = PermissionGauntletActivity::class.simpleName
    }
}
