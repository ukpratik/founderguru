// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    `kotlin-dsl`
}

group = "io.github.ukpratik.founderguru.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "fg.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "fg.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "fg.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
