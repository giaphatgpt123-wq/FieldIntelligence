plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.survivallibrary.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.survivallibrary.full"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.13.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
