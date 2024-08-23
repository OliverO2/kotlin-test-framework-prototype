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

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass = "MainKt"
        }
    }

    js {
        binaries.executable()
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
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

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        freeCompilerArgs = listOf(
            "-Xjdk-release=$jdkVersion",
            // NOTE: The following option will leak memory – https://youtrack.jetbrains.com/issue/KT-48678
            "-Xdebug" // Coroutine debugger: disable "was optimised out"
        )
    }
}

tasks.named { it.endsWith("Run") }.configureEach {
    extensions.extraProperties.set("idea.internal.test", true)
}
