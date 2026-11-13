plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Runs on a real phone: `./gradlew :app:generateReleaseBaselineProfile` records which code Laconic runs on its
// way to the first frame and while browsing, and the release build compiles that code ahead of time.
android {
    namespace = "cc.gyula.wave.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    useConnectedDevices = true
}

kotlin {
    compilerOptions {
        // A compiler warning fails the build, like a lint warning.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
