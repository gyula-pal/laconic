plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "cc.gyula.wave"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.gyula.wave"
        minSdk = 29
        targetSdk = 36
        versionCode = 15
        versionName = "0.9"
    }

    signingConfigs {
        // The key every Laconic release has been signed with, kept outside the repo: `wave.keystore` and
        // `wave.keystorePassword` in ~/.gradle/gradle.properties. Without them, as in a fresh clone, release
        // builds are signed with the debug key instead and won't install over an existing Laconic.
        val keystore = providers.gradleProperty("wave.keystore").orNull?.let(::file)?.takeIf { it.exists() }
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = providers.gradleProperty("wave.keystorePassword").get()
                keyAlias = "wave"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Lint runs with `./gradlew check`; any warning fails it. Hints stay hints.
        warningsAsErrors = true
        // Targets what Google Play requires (Android 16); Android 17's changes are still to be checked on the phone.
        disable += "OldTargetApi"
        // "A newer version is available" would fail the build on whichever day a library ships an update,
        // not because of anything in the change being built. Updates are done on purpose instead.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
    testOptions {
        // Robolectric runs the Android parts of the tests on the JVM, with the app's real resources (fonts).
        unitTests.isIncludeAndroidResources = true
    }
}

roborazzi {
    // Reference screenshots live with the tests: `./gradlew recordRoborazziDebug` rewrites them,
    // `./gradlew verifyRoborazziDebug` fails on any pixel that changed.
    outputDir.set(file("src/test/screenshots"))
}

kotlin {
    compilerOptions {
        // A compiler warning fails the build, like a lint warning.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    // Installs the baseline profile on a sideloaded phone, where no app store does it.
    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}
