import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    jvm()

    js {
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
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
            }
        }
    }
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        freeCompilerArgs = listOf(
            "-Xjdk-release=$jdkVersion",
            // NOTE: The following option will leak memory – https://youtrack.jetbrains.com/issue/KT-48678
            "-Xdebug" // Coroutine debugger: disable "was optimised out"
        )
    }
}
