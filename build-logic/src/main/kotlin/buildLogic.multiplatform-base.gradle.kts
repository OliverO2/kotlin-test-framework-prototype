import buildLogic.versionFromCatalog
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask

plugins {
    id("buildLogic.common")
    kotlin("multiplatform")
    id("com.gradleup.compat.patrouille")
    kotlin("plugin.atomicfu")
    id("org.jmailen.kotlinter")
}

compatPatrouille {
    java(project.versionFromCatalog("jdk").toInt())

    // We always stick to the same compiler version that our compiler plugin is adapted for.
    kotlin(project.versionFromCatalog("org.jetbrains.kotlin"))
}

kotlin {
    jvm()

    js {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser()
    }

    // Kotlin/Native target support – see https://kotlinlang.org/docs/native-target-support.html
    // Tier 1
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    iosArm64()
    // Tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()
    // Tier 3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()
    watchosDeviceArm64()
}

// WORKAROUND https://youtrack.jetbrains.com/issue/KT-78504 – NpmInstall tasks produce broken build cache entries
tasks.withType<KotlinNpmInstallTask>().configureEach {
    args.addAll(listOf("--network-concurrency", "1", "--mutex", "network"))
}
