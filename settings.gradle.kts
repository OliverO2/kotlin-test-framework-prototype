plugins {
    // https://github.com/Splitties/refreshVersions/releases
    id("de.fayard.refreshVersions") version "0.60.5"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
// //                                                   # available:"0.9.0"
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

rootProject.name = "testBalloon"

include(":framework-abstractions")
include(":framework-core")
include(":gradle-plugin")
include(":compiler-plugin")

include(":integrations-kotest-assertions")

include(":examples:framework-core")
include(":examples:integration-kotest-assertions")

include(":comparisons:using-kotlin-test")
include(":comparisons:using-testBalloon")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
