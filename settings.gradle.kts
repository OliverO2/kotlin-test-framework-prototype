plugins {
    // https://github.com/Splitties/refreshVersions/releases
    id("de.fayard.refreshVersions") version "0.60.5"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    pluginManagement {
        repositories {
            mavenCentral()
            specialRepositories()
            gradlePluginPortal()
        }
    }
    repositories {
        mavenCentral()
        specialRepositories()
    }
}

refreshVersions {
    featureFlags {
        enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
    }
}

fun RepositoryHandler.specialRepositories() {
    maven(url = "${System.getenv("HOME")!!}/.m2/local-repository")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
}

rootProject.name = "kotlin-test-framework-prototype"

include(":test-framework-core")
include(":test-framework-gradle-plugin")
include(":test-framework-compiler-plugin")

include(":application")
