plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } namespace="vn.fieldintel.domain.emergency"; compileSdk=35; defaultConfig { minSdk=26 }; testOptions { unitTests.isReturnDefaultValues=true } }
dependencies { testImplementation("junit:junit:4.13.2") }
