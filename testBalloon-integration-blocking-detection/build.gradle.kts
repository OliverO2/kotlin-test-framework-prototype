import buildLogic.addTestBalloonPluginFromProject

plugins {
    id("buildLogic.multiplatform-all")
    id("buildLogic.publishing-multiplatform")
}

description = "Library supporting blocking code detection with the TestBalloon framework"

addTestBalloonPluginFromProject(projects.testBalloonCompilerPlugin, projects.testBalloonFrameworkAbstractions)

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.testBalloonFrameworkCore)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test")) // for assertions only
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.io.projectreactor.tools.blockhound)
                implementation(libs.org.jetbrains.kotlinx.coroutines.debug)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            val javaLauncher = javaLauncher.orNull
            buildList {
                if (javaLauncher != null && javaLauncher.metadata.languageVersion >= JavaLanguageVersion.of(16)) {
                    // https://github.com/reactor/BlockHound/issues/33
                    add("-XX:+AllowRedefinitionToAddDeleteMethods")
                }
            }
        }
    )
}
