import buildLogic.versionFromCatalog

plugins {
    id("buildLogic.common")
    kotlin("jvm")
    id("com.gradleup.compat.patrouille")
}

compatPatrouille {
    java(project.versionFromCatalog("jdk").toInt())

    // We always stick to the same compiler version that our compiler plugin is adapted for.
    kotlin(project.versionFromCatalog("org.jetbrains.kotlin"))
}
