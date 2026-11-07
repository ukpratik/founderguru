// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("fg.android.application")
    id("fg.android.compose")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "io.github.ukpratik.founderguru"

    defaultConfig {
        applicationId = "io.github.ukpratik.founderguru" // permanent (D-020, ADR-009)
        // versionCode = MAJOR × 10000 + MINOR × 100 + PATCH (LLD §9, ADR-006): 1.0.0 → 10000.
        versionCode = 10000
        versionName = "1.0.0"
    }

    // D-021 / ADR-006: the GitHub-release key lives in ~/.android-keys/, its passwords in ~/.gradle/gradle.properties
    // (founderguru.release.*), never in this repo. Without those properties release builds stay unsigned (CI, F-Droid).
    signingConfigs {
        providers.gradleProperty("founderguru.release.storeFile").orNull?.let { storeFilePath ->
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("founderguru.release.storePassword").get()
                keyAlias = providers.gradleProperty("founderguru.release.keyAlias").get()
                keyPassword = providers.gradleProperty("founderguru.release.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Reproducible builds (F-Droid, LLD §9): no git metadata in the APK.
            vcsInfo.include = false
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

    // MigrationTestHelper reads the exported schemas from the app's assets; Robolectric only sees the variant's merged
    // assets, so the schemas go into debug builds (a few KB, never release) for the migration tests (NFR-012).
    sourceSets.getByName("debug").assets.directories.add("schemas")
}

// LLD §2.1, NFR-012: the Room schema JSON is exported and committed; CI fails if it drifts.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// US-013, ADR-007 licence gate (as in Lantern Sudoku): generated at build time, fully offline. Strict mode fails the
// release build on any licence outside the GPL-3.0-compatible allowlist. The same report feeds About › Open-source licences.
aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
        filterVariants.addAll("release")
    }
    export {
        excludeFields.addAll("generated", "funding", "developers")
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "OFL-1.1")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
