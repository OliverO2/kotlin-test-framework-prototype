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
    maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
    // maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
    //     name = "MavenCentralSnapshots"
    //     mavenContent { snapshotsOnly() }
    // }
}

rootProject.name = "kotlin-test-framework-prototype"

include(":test-framework")
include(":application")
