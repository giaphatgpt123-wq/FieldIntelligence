package vn.survivallibrary.app

import android.app.Application

/**
 * Lightweight application bootstrap. It only reconciles native JobScheduler jobs and cache budgets;
 * it never opens the network, database, recognition model or media files on the UI startup thread.
 */
class SurvivalLibraryNativeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LibrarySyncScheduler.reconcile(this)
        MediaCachePolicy.trimToBudget(this)
    }
}
