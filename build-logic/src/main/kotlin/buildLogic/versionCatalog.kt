package buildLogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the

fun Project.versionFromCatalog(alias: String): String =
    the<VersionCatalogsExtension>().named("libs").findVersion(alias).get().displayName

fun Project.libraryFromCatalog(alias: String): String =
    the<VersionCatalogsExtension>().named("libs").findLibrary(alias).get().get().toString()
