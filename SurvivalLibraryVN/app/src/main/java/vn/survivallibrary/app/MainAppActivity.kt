package vn.survivallibrary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Compose application host, intentionally separated from the native safe launcher. */
class MainAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SurvivalLibraryRoot() }
    }
}
