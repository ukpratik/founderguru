// SPDX-License-Identifier: GPL-3.0-or-later
// :tools:feedcheck (NFR-014, ADR-008): a JVM CLI that runs the app's own :core fetch + clean + parse on every source in
// sources.json and writes a health report for the weekly feed-health workflow. Never shipped in the APK.
plugins {
    id("fg.jvm.library")
    application
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass.set("io.github.ukpratik.founderguru.tools.feedcheck.MainKt")
}

dependencies {
    implementation(project(":core"))
    // XmlPullParser on the plain JVM (Android provides it in the app).
    implementation(libs.kxml2)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}

// Paths in --args are relative to the repository root (as the workflow passes them).
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
