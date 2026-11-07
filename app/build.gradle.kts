// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("fg.android.application")
    id("fg.android.compose")
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.ukpratik.founderguru"

    defaultConfig {
        applicationId = "io.github.ukpratik.founderguru" // permanent (D-020, ADR-009)
        // versionCode = MAJOR × 10000 + MINOR × 100 + PATCH (LLD §9). Pre-release development builds stay at 0.x.
        versionCode = 100
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // No signing config yet (D-021 lands with the first release): release builds are unsigned, as on CI and F-Droid.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { buildConfig = true }

    // English-only UI (LLD §9, NFR-013).
    androidResources { localeFilters += "en" }

    // Reproducible builds for F-Droid (US-013, LLD §9).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Robolectric tests (Room, repositories) need the merged resources and assets (LLD §8).
    testOptions.unitTests.isIncludeAndroidResources = true
}

// LLD §2.1, NFR-012: the Room schema JSON is exported and committed; CI fails if it drifts.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.tls)
}
