package com.michaeltchuang.seedvault.demo.lightclient

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks the Seed Vault light clients about every 15 minutes, the shortest period WorkManager has. Algorand
 * makes a state proof every 256 rounds (about 12 minutes), so this keeps up. Android may run it later when
 * the device is idle. A failed check is logged and tried again next time, it never fails the job.
 */
class LightClientWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LightClientRunner(applicationContext).check()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "light_client_check"

        /** Safe to call on every start, an already scheduled job is kept. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<LightClientWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
