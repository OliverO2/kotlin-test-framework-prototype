import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs = listOf("-Xjdk-release=$jdkVersion")
        }
    }

    js {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    linuxX64 {
        binaries.sharedLib()
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
                api(libs.org.jetbrains.kotlinx.coroutines.core)
                implementation(libs.org.jetbrains.kotlinx.datetime)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.org.junit.platform.engine)
            }
        }
    }
}
