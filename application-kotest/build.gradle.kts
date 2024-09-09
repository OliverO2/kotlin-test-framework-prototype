import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.io.kotest.multiplatform)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
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

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(libs.io.kotest.kotest.framework.engine)
                implementation(libs.io.kotest.kotest.assertions.core)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.io.kotest.kotest.runner.junit5)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // https://docs.gradle.org/current/userguide/java_testing.html
    useJUnitPlatform()
}
