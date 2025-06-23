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
    System.getProperty("user.home")?.let { home ->
        maven(url = uri("$home/.m2/local-repository"))
    }
    maven("https://redirector.kotlinlang.org/maven/dev")
    gradlePluginPortal()
}

refreshVersions {
    featureFlags {
        enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
