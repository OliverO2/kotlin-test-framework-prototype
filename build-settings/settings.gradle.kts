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

rootProject.name = "build-settings"
