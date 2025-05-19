import buildLogic.addTestBalloonPluginFromProject

plugins {
    id("buildLogic.multiplatform-noWasmWasi")
}

addTestBalloonPluginFromProject(projects.testBalloonCompilerPlugin, projects.testBalloonFrameworkAbstractions)

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                // implementation(libs.de.infix.testBalloon.integration.kotest.assertions) // Use this outside this project
                implementation(projects.testBalloonIntegrationKotestAssertions)
            }
        }
    }
}
