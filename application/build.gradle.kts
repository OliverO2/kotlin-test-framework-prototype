import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
        mainRun {
            mainClass = "MainKt"
        }
        compilerOptions {
            freeCompilerArgs = listOf("-Xjdk-release=$jdkVersion")
        }
    }

    js {
        binaries.executable()
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
        nodejs()
    }

    linuxX64 {
        binaries.executable()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        group("common") {
            group("jsHosted") {
                withJs()
                withWasmJs()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":test-framework"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.org.jetbrains.kotlinx.coroutines.debug)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // https://docs.gradle.org/current/userguide/java_testing.html
    useJUnitPlatform()

    // Show stdout/stderr and stack traces on console – https://stackoverflow.com/q/65573633/2529022
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
        // showStackTraces = true
    }

    filter {
        @Suppress("RemoveExplicitTypeArguments", "RedundantSuppression")
        listOf<String>(
            // "com.example.TestScope2*"
        ).forEach {
            includeTestsMatching(it)
        }
    }
}

// tasks.named { it.endsWith("Run") }.configureEach {
//     extensions.extraProperties.set("idea.internal.test", true)
// }
