plugins {
    id("de.fayard.refreshVersions")
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    pluginManagement {
        repositories {
            projectRepositories()
        }
    }
    @Suppress("UnstableApiUsage")
    repositories {
        projectRepositories()
    }
}

fun RepositoryHandler.projectRepositories() {
    mavenCentral()
    maven(url = uri("${System.getenv("HOME")!!}//.m2/local-repository"))
    gradlePluginPortal()
}

refreshVersions {
    featureFlags {
        enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
