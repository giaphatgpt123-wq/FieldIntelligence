package vn.survivallibrary.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime policies agreed for the production direction:
 * - library data updates automatically only on unmetered networks (Wi-Fi/Ethernet) by default;
 * - APK updates remain user-confirmed;
 * - media/cache budgets are independent from the APK and can be tuned by storage profile;
 * - scheduling never opens SQLite or the network on the UI startup path.
 */
enum class LibrarySyncMode {
    WIFI_AUTO,
    NOTIFY_ONLY,
    MANUAL
}

enum class StorageProfile(val label: String, val mediaCacheBudgetMb: Int) {
    LIGHT("Nhẹ", 120),
    STANDARD("Tiêu chuẩn", 450),
    FULL_OFFLINE("Offline đầy đủ", 2048)
}

object RuntimePolicyStore {
    private const val PREF = "runtime_optimization_policy"
    private const val KEY_SYNC_MODE = "sync_mode"
    private const val KEY_STORAGE_PROFILE = "storage_profile"
    private const val KEY_LAST_SYNC_AT = "last_library_sync_at"
    private const val KEY_LAST_SYNC_MESSAGE = "last_library_sync_message"
    private const val KEY_LAST_SYNC_RECORDS = "last_library_sync_records"

    fun syncMode(context: Context): LibrarySyncMode {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_SYNC_MODE, LibrarySyncMode.WIFI_AUTO.name)
        return runCatching { LibrarySyncMode.valueOf(raw ?: LibrarySyncMode.WIFI_AUTO.name) }
            .getOrDefault(LibrarySyncMode.WIFI_AUTO)
    }

    fun setSyncMode(context: Context, mode: LibrarySyncMode) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_SYNC_MODE, mode.name).apply()
        runCatching { LibrarySyncScheduler.reconcile(context) }
    }

    fun storageProfile(context: Context): StorageProfile {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_STORAGE_PROFILE, StorageProfile.LIGHT.name)
        return runCatching { StorageProfile.valueOf(raw ?: StorageProfile.LIGHT.name) }
            .getOrDefault(StorageProfile.LIGHT)
    }

    fun setStorageProfile(context: Context, profile: StorageProfile) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_STORAGE_PROFILE, profile.name).apply()
        Thread({ runCatching { MediaCachePolicy.trimToBudget(context.applicationContext) } }, "library-cache-trim").start()
    }

    fun recordLibrarySync(context: Context, result: UpdateRunResult) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .putString(KEY_LAST_SYNC_MESSAGE, result.message)
            .putInt(KEY_LAST_SYNC_RECORDS, result.installedRecords)
            .apply()
    }

    fun lastSyncAt(context: Context): Long = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_SYNC_AT, 0L)

    fun lastSyncMessage(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(KEY_LAST_SYNC_MESSAGE, "Chưa có lần đồng bộ tự động nào.")
        ?: "Chưa có lần đồng bộ tự động nào."

    fun lastSyncRecords(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getInt(KEY_LAST_SYNC_RECORDS, 0)
}

object LibrarySyncScheduler {
    const val ONE_SHOT_JOB_ID = 7301
    const val PERIODIC_JOB_ID = 7302
    private const val TWELVE_HOURS_MS = 12L * 60L * 60L * 1000L
    private const val THIRTY_MINUTES_MS = 30L * 60L * 1000L

    fun reconcile(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (RuntimePolicyStore.syncMode(context) != LibrarySyncMode.WIFI_AUTO) {
            scheduler.cancel(ONE_SHOT_JOB_ID)
            scheduler.cancel(PERIODIC_JOB_ID)
            return
        }

        val component = ComponentName(context, LibrarySyncJobService::class.java)

        if (scheduler.getPendingJob(ONE_SHOT_JOB_ID) == null) {
            scheduler.schedule(
                JobInfo.Builder(ONE_SHOT_JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setBackoffCriteria(THIRTY_MINUTES_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build()
            )
        }

        if (scheduler.getPendingJob(PERIODIC_JOB_ID) == null) {
            scheduler.schedule(
                JobInfo.Builder(PERIODIC_JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setPeriodic(TWELVE_HOURS_MS)
                    .setBackoffCriteria(THIRTY_MINUTES_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build()
            )
        }
    }
}

class LibrarySyncJobService : JobService() {
    /**
     * Keep cancellation state per JobScheduler job. A single shared boolean is unsafe because
     * one-shot and periodic jobs can overlap on some devices, and a newly started job could
     * accidentally re-enable completion of an older stopped worker.
     */
    private val cancelledJobs = ConcurrentHashMap<Int, AtomicBoolean>()

    override fun onStartJob(params: JobParameters): Boolean {
        val cancelled = AtomicBoolean(false)
        cancelledJobs[params.jobId] = cancelled

        Thread({
            var retry = false
            try {
                if (!cancelled.get() && RuntimePolicyStore.syncMode(this) == LibrarySyncMode.WIFI_AUTO) {
                    val result = LibraryUpdateEngine.checkAndUpdate(applicationContext)
                    if (!cancelled.get()) {
                        RuntimePolicyStore.recordLibrarySync(applicationContext, result)
                        MediaCachePolicy.trimToBudget(applicationContext)
                        retry = !result.checked && result.errors.isNotEmpty()
                    }
                }
            } catch (_: Throwable) {
                retry = true
            } finally {
                cancelledJobs.remove(params.jobId, cancelled)
                if (!cancelled.get()) jobFinished(params, retry)
            }
        }, "library-sync-${params.jobId}").start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        cancelledJobs.remove(params.jobId)?.set(true)
        return true
    }
}

/**
 * Media is deliberately stored outside the APK. The cache is LRU-trimmed by lastModified time.
 * High-resolution media/model packages should live in dedicated app files, not this cache.
 */
object MediaCachePolicy {
    private const val MEDIA_CACHE_DIR = "library-media"

    fun cacheDirectory(context: Context): File = File(context.cacheDir, MEDIA_CACHE_DIR).apply { mkdirs() }

    fun budgetBytes(context: Context): Long {
        return RuntimePolicyStore.storageProfile(context).mediaCacheBudgetMb.toLong() * 1024L * 1024L
    }

    fun currentBytes(context: Context): Long = directorySize(cacheDirectory(context))

    fun trimToBudget(context: Context): Long {
        val root = cacheDirectory(context)
        val budget = budgetBytes(context)
        var total = directorySize(root)
        if (total <= budget) return total

        val files = root.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()

        for (file in files) {
            if (total <= budget) break
            val size = file.length()
            if (file.delete()) total -= size
        }
        return total.coerceAtLeast(0L)
    }

    fun clear(context: Context): Long {
        val root = cacheDirectory(context)
        root.walkBottomUp().filter { it != root }.forEach { it.delete() }
        return currentBytes(context)
    }

    private fun directorySize(root: File): Long {
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
