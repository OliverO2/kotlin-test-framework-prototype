import buildLogic.addTestBalloonPluginFromProject

plugins {
    id("buildLogic.jvm")
}

addTestBalloonPluginFromProject(projects.testBalloonCompilerPlugin, projects.testBalloonFrameworkAbstractions)

dependencies {
    testImplementation(projects.testBalloonFrameworkCore)
    testImplementation(kotlin("test")) // for assertions only
}

tasks {
    val integrationTestRepositoryDir = rootProject.layout.buildDirectory.dir("integration-test-repository")
    val scenariosSourceDirectory = project.layout.projectDirectory.dir("scenarioTemplates")
    val scenariosBuildDirectory = project.layout.buildDirectory.dir("scenarioTemplates")

    val updateIntegrationTestRepository by registering(Exec::class) {
        group = "verification"
        description = "Updates the project's artifacts in the integration test repository."

        outputs.dir(integrationTestRepositoryDir)
        outputs.upToDateWhen { false }

        workingDir = rootDir
        commandLine = mutableListOf("./gradlew", "publishAllPublicationsToIntegrationTestRepository")
    }

    val syncScenarioBuildGradleConfiguration by registering(Copy::class) {
        group = "verification"
        description = "Synchronizes the Gradle configuration in the scenario build directory" +
            " with the root project's configuration."

        val primaryProjectDirectory = rootDir

        from(primaryProjectDirectory)
        into(scenariosBuildDirectory.map { it.dir("common") })
        include("gradlew*", "gradle/**")
        include("kotlin-js-store/**")
    }

    val syncScenarioBuildFiles by registering(Copy::class) {
        group = "verification"
        description = "Synchronizes the scenario build directory with scenario sources."

        dependsOn(syncScenarioBuildGradleConfiguration)

        val baseVersions = with(project.the<VersionCatalogsExtension>().named("libs")) {
            versionAliases.associateWith { findVersion(it).get().displayName }
        }
        val basePluginVersions = with(project.the<VersionCatalogsExtension>().named("libs")) {
            pluginAliases.associateWith { findPlugin(it).get().get().version.toString() }
        }
        val baseProperties = project.properties.map { it.key!! to it.value.toString() }.toMap()
        val replaceRegex = Regex("""\{\{(.*?)\}\}""")

        inputs.property("baseVersions", baseVersions)
        inputs.property("basePluginVersions", basePluginVersions)
        inputs.property("baseProperties", baseProperties)

        from(scenariosSourceDirectory)
        into(scenariosBuildDirectory)
        filter { line ->
            line.replace(replaceRegex) { matchResult ->
                val (protocol, name) = matchResult.groupValues[1].split(':')
                when (protocol) {
                    "version" -> baseVersions[name] ?: "??version alias '$name' not found??"
                    "pluginVersion" -> basePluginVersions[name] ?: "??plugin alias '$name' not found??"
                    "prop" -> baseProperties[name] ?: "??property '$name' not found??"
                    "path" -> when (name) {
                        "integration-test-repository" -> integrationTestRepositoryDir.get().toString()
                        else -> "??unknown path name '$name'??"
                    }

                    else -> matchResult.value
                }
            }
        }
        filteringCharset = "UTF-8"
    }

    named("test") {
        dependsOn(updateIntegrationTestRepository)
        inputs.dir(integrationTestRepositoryDir)

        dependsOn(syncScenarioBuildFiles)
        inputs.dir(syncScenarioBuildFiles.map { it.destinationDir })
    }
}
