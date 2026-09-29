package vn.survivallibrary.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Keeps library data independent from the APK and checks for verified packages
 * only on an unmetered network. Scheduling is cheap; all network/database work
 * happens later in JobService, never on the UI startup path.
 */
object WifiLibraryUpdateScheduler {
    private const val JOB_ID = 0x534C56
    private const val PERIOD_MS = 6L * 60L * 60L * 1000L
    private const val FLEX_MS = 60L * 60L * 1000L

    fun ensureScheduled(context: Context): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        if (scheduler.getPendingJob(JOB_ID) != null) return true

        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, WifiLibraryUpdateJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setPersisted(true)
            .setPeriodic(PERIOD_MS, FLEX_MS)
            .build()

        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }
}

class WifiLibraryUpdateJobService : JobService() {
    @Volatile
    private var running = false

    override fun onStartJob(params: JobParameters): Boolean {
        if (running) return false
        running = true
        Thread {
            val result = LibraryUpdateEngine.checkAndUpdate(applicationContext)
            runCatching {
                OfflineLibraryDb(applicationContext).use { db ->
                    db.setMeta("last_auto_update_checked", System.currentTimeMillis().toString())
                    db.setMeta("last_auto_update_ok", result.checked.toString())
                    db.setMeta("last_auto_update_message", result.message.take(500))
                }
            }
            running = false
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running = false
        return true
    }
}
