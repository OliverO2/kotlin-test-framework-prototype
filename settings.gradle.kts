plugins {
    // https://github.com/Splitties/refreshVersions/releases
    id("de.fayard.refreshVersions") version "0.60.5"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }
    repositories {
        mavenCentral()
    }
}

refreshVersions {
    featureFlags {
        enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
    }
}

rootProject.name = "kotlin-test-framework-prototype"

include(":test-framework")
include(":application")
