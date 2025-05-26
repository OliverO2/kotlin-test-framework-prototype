import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("buildLogic.common")
    kotlin("multiplatform")
    id("com.gradleup.compat.patrouille")
    kotlin("plugin.atomicfu")
    id("org.jmailen.kotlinter")
}

compatPatrouille {
    java(project.property("local.jdk.version").toString().toInt())

    // We always stick to the same compiler version that our compiler plugin is adapted for.
    @OptIn(ExperimentalBuildToolsApi::class, ExperimentalKotlinGradlePluginApi::class)
    kotlin(project.kotlin.compilerVersion.get())
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

    linuxX64 {
        binaries.sharedLib()
    }
}
