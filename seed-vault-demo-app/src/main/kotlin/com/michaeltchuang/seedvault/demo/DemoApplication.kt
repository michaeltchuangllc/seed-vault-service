package com.michaeltchuang.seedvault.demo

import android.app.Application
import com.michaeltchuang.seedvault.demo.lightclient.LightClientWorker

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keeps the Seed Vault light clients up to date, whether or not the app is open
        LightClientWorker.schedule(this)
    }
}
