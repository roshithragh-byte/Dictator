plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    tasks.matching { it.name.contains("AarMetadata") }.configureEach {
        enabled = false
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }
    plugins.withId("com.android.library") {
        dependencies.add("testImplementation", "junit:junit:4.13.2")
        configure<com.android.build.api.dsl.LibraryExtension> {
            lint {
                abortOnError = false
                checkReleaseBuilds = false
            }
        }
    }
    plugins.withId("com.android.application") {
        dependencies.add("testImplementation", "junit:junit:4.13.2")
        configure<com.android.build.api.dsl.ApplicationExtension> {
            lint {
                abortOnError = false
                checkReleaseBuilds = false
            }
        }
    }
}
