plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val duongODauVersionCode = providers.gradleProperty("DUONGODAU_VERSION_CODE").orNull?.toIntOrNull() ?: 2
val duongODauVersionName = providers.gradleProperty("DUONGODAU_VERSION_NAME").orNull ?: "0.2.0"
val stableKeyStorePath = System.getenv("DUONGODAU_KEYSTORE_PATH")
val stableStorePassword = System.getenv("DUONGODAU_STORE_PASSWORD")
val stableKeyPassword = System.getenv("DUONGODAU_KEY_PASSWORD")

android {
    namespace = "vn.duongodau.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.duongodau.app"
        minSdk = 26
        targetSdk = 35
        versionCode = duongODauVersionCode
        versionName = duongODauVersionName
    }

    signingConfigs {
        if (!stableKeyStorePath.isNullOrBlank() && !stableStorePassword.isNullOrBlank() && !stableKeyPassword.isNullOrBlank()) {
            create("stable") {
                storeFile = file(stableKeyStorePath)
                storePassword = stableStorePassword
                keyAlias = "duongodau"
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
    testImplementation("junit:junit:4.13.2")
}
