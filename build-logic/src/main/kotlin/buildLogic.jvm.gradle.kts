import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("buildLogic.common")
    kotlin("jvm")
    id("com.gradleup.compat.patrouille")
}

compatPatrouille {
    java(project.property("local.jdk.version").toString().toInt())

    // We always stick to the same compiler version that our compiler plugin is adapted for.
    @OptIn(ExperimentalBuildToolsApi::class, ExperimentalKotlinGradlePluginApi::class)
    kotlin(project.kotlin.compilerVersion.get())
}
