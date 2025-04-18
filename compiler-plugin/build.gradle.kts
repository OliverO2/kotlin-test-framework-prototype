import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.com.github.gmazzo.buildconfig)
    alias(libs.plugins.com.vanniktech.maven.publish)
    alias(libs.plugins.org.jmailen.kotlinter)
}

group = project.property("local.PROJECT_GROUP_ID")!!

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
                implementation(projects.frameworkAbstractions)
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

    buildConfigField(
        "String",
        "PROJECT_COMPILER_PLUGIN_ID",
        "\"${project.property("local.PROJECT_COMPILER_PLUGIN_ID")}\""
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "local"
            url = uri("${System.getenv("HOME")!!}//.m2/local-repository")
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
