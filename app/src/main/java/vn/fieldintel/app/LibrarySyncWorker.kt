package vn.fieldintel.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class LibrarySyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!DataUpdateManager(applicationContext).isWifiAvailable()) return Result.retry()
        return runCatching {
            val updates = DataUpdateManager(applicationContext)
            val manifest = updates.parseManifest(updates.fetchText(UpdateConfig.MANIFEST_URL))
            val downloaded = updates.download(UpdateConfig.PACKAGE_URL, manifest, applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).longVersionCode.toInt())
            if (!downloaded.applied) error(downloaded.message)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("scientific-library-wifi-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
