plugins {
    // https://github.com/Splitties/refreshVersions/releases
    id("de.fayard.refreshVersions") version "0.60.5"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
// //                                                   # available:"1.0.0-rc-1"
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

include(":testBalloon-framework-abstractions")
include(":testBalloon-framework-core")
include(":testBalloon-gradle-plugin")
include(":testBalloon-compiler-plugin")

include(":testBalloon-integration-kotest-assertions")
include(":testBalloon-integration-blocking-detection")

include(":examples:framework-core")
include(":examples:integration-kotest-assertions")

include(":comparisons:using-kotlin-test")
include(":comparisons:using-testBalloon")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
