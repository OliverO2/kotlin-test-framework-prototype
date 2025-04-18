import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.com.vanniktech.maven.publish)
    alias(libs.plugins.org.jmailen.kotlinter)
}

group = project.property("local.PROJECT_GROUP_ID")!!

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjdk-release=$jdkVersion")
        }
    }

    js {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser()
    }

    // @OptIn(ExperimentalWasmDsl::class)
    // wasmWasi {
    //     nodejs()
    // }

    linuxX64 {
        binaries.executable()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        group("common") {
            group("nonJvm") {
                withJs()
                withWasmJs()
                // withWasmWasi()
                withLinux()
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.frameworkCore)
                api(libs.io.kotest.assertions.core)
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "local"
            url = uri("${System.getenv("HOME")!!}//.m2/local-repository")
        }
    }
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
