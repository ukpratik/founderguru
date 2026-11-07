// SPDX-License-Identifier: GPL-3.0-or-later
// Pure Kotlin pipeline: fetch -> clean -> parse -> normalise (and, from M2, tag/blend/trend). No android.* (ADR-008, LLD §1.1).
plugins {
    id("fg.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // Only the org.xmlpull.v1 interfaces: Android provides them at runtime. Never shipped in the APK (ADR-008).
    compileOnly(libs.kxml2)

    testImplementation(libs.kxml2)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The shipped catalogue is validated by CatalogLoaderTest (LLD §2.3).
tasks.test {
    val sources = rootProject.layout.projectDirectory.file("app/src/main/assets/sources.json")
    inputs.file(sources).withPropertyName("sourcesJson")
    systemProperty("fg.sourcesJson", sources.asFile.path)
    // -Pfg.live=true runs LiveFeedsTest against the real 24 feeds (network; never in CI).
    providers.gradleProperty("fg.live").orNull?.let { systemProperty("fg.live", it) }
    testLogging { events("failed"); showStandardStreams = providers.gradleProperty("fg.live").isPresent }
}
