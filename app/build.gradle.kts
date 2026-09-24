plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="vn.fieldintel.app"; compileSdk=35
 defaultConfig { applicationId="vn.fieldintel.app"; minSdk=26; targetSdk=35; versionCode=1; versionName="0.1.0-p0" }
 buildFeatures { compose=true }
}
dependencies {
 implementation(project(":feature:emergency"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3:1.3.1")
}
