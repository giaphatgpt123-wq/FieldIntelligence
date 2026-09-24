plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("com.google.devtools.ksp") }
android { namespace="vn.fieldintel.data.emergency"; compileSdk=35; defaultConfig { minSdk=26 } }
dependencies {
 implementation(project(":domain:emergency")); implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
}
