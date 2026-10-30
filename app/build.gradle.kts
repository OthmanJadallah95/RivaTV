plugins {
    id("com.android.application")
}

// Plain Java + Android framework only (no external libraries), so the app can also be
// built with the bare SDK tools: see build.sh.
android {
    namespace = "com.gguard.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riva.tv"
        minSdk = 21
        targetSdk = 29
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
