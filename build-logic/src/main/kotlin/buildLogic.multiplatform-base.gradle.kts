import buildLogic.versionFromCatalog
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

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

    linuxX64 {
        binaries.sharedLib()
    }
}
