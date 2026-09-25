import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val pilotSigningFile = layout.buildDirectory.file("pilot-test-signing.p12").get().asFile
pilotSigningFile.parentFile.mkdirs()
pilotSigningFile.writeBytes(Base64.getDecoder().decode(file("pilot-test-signing.p12.b64").readText().trim()))

android {
    namespace = "vn.fieldintel.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "vn.fieldintel.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.2-observations-pilot"
        manifestPlaceholders["appLabel"] = "VN Sinh tồn"
    }
    signingConfigs {
        create("pilotDebug") {
            storeFile = pilotSigningFile
            storePassword = "pilot-test-only"
            keyAlias = "pilot-debug"
            keyPassword = "pilot-test-only"
            storeType = "pkcs12"
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".osmtest"
            signingConfig = signingConfigs.getByName("pilotDebug")
            versionNameSuffix = "-test"
            manifestPlaceholders["appLabel"] = "VN Sinh tồn OSM thử nghiệm"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":feature:emergency"))
    implementation(project(":data:emergency"))
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3:1.3.1")
    testImplementation("junit:junit:4.13.2")
}
