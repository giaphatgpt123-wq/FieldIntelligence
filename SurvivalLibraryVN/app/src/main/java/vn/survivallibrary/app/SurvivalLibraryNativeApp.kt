package vn.survivallibrary.app

import android.app.Application

/**
 * Lightweight application bootstrap.
 * Scheduling is fail-soft and cache scanning never runs on the main startup thread.
 */
class SurvivalLibraryNativeApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // OEM JobScheduler implementations must never be able to crash app startup.
        runCatching { LibrarySyncScheduler.reconcile(this) }

        // A large media cache can contain thousands of files. Walking it on the main
        // thread can cause slow startup/ANR, so trimming is deliberately asynchronous.
        Thread({
            runCatching { MediaCachePolicy.trimToBudget(applicationContext) }
        }, "library-media-trim").start()
    }
}
