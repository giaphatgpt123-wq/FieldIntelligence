plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="vn.fieldintel.feature.emergency"; compileSdk=35; defaultConfig { minSdk=26 }; buildFeatures { compose=true } }
dependencies { implementation(project(":domain:emergency")); implementation("androidx.compose.material3:material3:1.3.1") }
