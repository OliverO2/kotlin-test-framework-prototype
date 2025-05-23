import buildLogic.addTestBalloonPluginFromProject

plugins {
    id("buildLogic.multiplatform-noWasmWasi")
    id("buildLogic.publishing-multiplatform")
}

description = "Library supporting Kotest Assertions with the TestBalloon framework"

addTestBalloonPluginFromProject(projects.testBalloonCompilerPlugin, projects.testBalloonFrameworkAbstractions)

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.testBalloonFrameworkCore)
                api(libs.io.kotest.assertions.core)
            }
        }
    }
}
