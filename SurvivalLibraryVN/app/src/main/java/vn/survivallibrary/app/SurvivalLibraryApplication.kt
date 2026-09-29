package vn.survivallibrary.app

import android.app.Application

class SurvivalLibraryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only registers a persisted Android job. No network or database work is
        // performed synchronously on the application startup path.
        WifiLibraryUpdateScheduler.ensureScheduled(this)
    }
}
