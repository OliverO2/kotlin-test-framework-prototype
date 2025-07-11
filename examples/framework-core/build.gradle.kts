import buildLogic.addTestBalloonPluginFromProject

plugins {
    id("buildLogic.multiplatform-all")
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
}

addTestBalloonPluginFromProject(projects.testBalloonCompilerPlugin, projects.testBalloonFrameworkAbstractions)

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                // implementation(libs.de.infix.testBalloon.framework.core) // Use this outside this project
                implementation(projects.testBalloonFrameworkCore)
                implementation(kotlin("test")) // for assertions only
                implementation(libs.org.jetbrains.kotlinx.atomicfu)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.org.jetbrains.kotlinx.coroutines.debug)
            }
        }
    }
}
