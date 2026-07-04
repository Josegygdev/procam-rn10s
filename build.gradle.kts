plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.procam.rn10s"
    // Compilado contra API 34, pero el objetivo real de ejecución es
    // Android 13 (API 33) — build TPA1A.220624.014 del Redmi Note 10S.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.procam.rn10s"
        minSdk = 30
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0-fase1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    val cameraxVersion = "1.3.4"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX: preview, captura de video, y el puente Camera2Interop
    // que usamos para exponer ISO, obturador, WB manual y el tonemap
    // curve necesario para el perfil LOG.
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")
}
