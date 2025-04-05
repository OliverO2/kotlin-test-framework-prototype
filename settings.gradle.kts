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

rootProject.name = "kotlin-test-framework-prototype"

include(":test-framework-abstractions")
include(":test-framework-core")
include(":test-framework-gradle-plugin")
include(":test-framework-compiler-plugin")

include(":test-framework-integrations:kotest-assertions")

include(":samples:prototype-scenarios")
include(":samples:using-kotlin-test")
include(":samples:using-prototype")
include(":samples:using-prototype-with-kotest-assertions")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
