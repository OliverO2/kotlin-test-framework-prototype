import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.com.github.gmazzo.buildconfig)
    `maven-publish`
    alias(libs.plugins.org.jmailen.kotlinter)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjdk-release=$jdkVersion")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.testFrameworkAbstractions)
            }
        }

        jvmMain {
            dependencies {
                implementation(kotlin("compiler-embeddable"))
                implementation(libs.org.jetbrains.kotlinx.coroutines.core)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.dev.zacsweers.kctfork)
                implementation(kotlin("test"))
            }
        }
    }
}

buildConfig {
    packageName("buildConfig")
    useKotlinOutput { internalVisibility = true }

    buildConfigField("String", "TEST_FRAMEWORK_PLUGIN_ID", "\"${project.property("local.TEST_FRAMEWORK_PLUGIN_ID")}\"")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

apply(from = "../build-publish-local.gradle.kts")

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["kotlin"])

            pom {
                name.set(project.name)
                description.set("Kotlin compiler plugin for test discovery")
            }
        }
    }
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}

tasks.withType<org.jmailen.gradle.kotlinter.tasks.LintTask> {
    source = source.minus(fileTree("build")).asFileTree
}

tasks.withType<org.jmailen.gradle.kotlinter.tasks.FormatTask> {
    source = source.minus(fileTree("build")).asFileTree
}
