plugins {
    id("com.android.application")
}

android {
    namespace = "it.gallipoli.webcam"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.gallipoli.webcamtv"
        minSdk = 22
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // chiave di firma fissa: ogni APK creato da GitHub ha la stessa firma,
    // cosi' gli aggiornamenti si installano sopra la versione precedente
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
