plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.com.github.gmazzo.buildconfig)
    `java-gradle-plugin`
    alias(libs.plugins.com.gradle.plugin.publish)
    alias(libs.plugins.org.jmailen.kotlinter)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
    packageName("buildConfig")
    useKotlinOutput { internalVisibility = true }

    val compilerPluginProject = projects.compilerPlugin
    buildConfigField("String", "TEST_FRAMEWORK_PLUGIN_ID", "\"${project.property("local.TEST_FRAMEWORK_PLUGIN_ID")}\"")
    buildConfigField("String", "TEST_FRAMEWORK_VERSION", "\"${project.version}\"")
    buildConfigField("String", "TEST_FRAMEWORK_COMPILER_PLUGIN_GROUP_ID", "\"${compilerPluginProject.group}\"")
    buildConfigField("String", "TEST_FRAMEWORK_COMPILER_PLUGIN_ARTIFACT_ID", "\"${compilerPluginProject.name}\"")
    buildConfigField("String", "TEST_FRAMEWORK_COMPILER_PLUGIN_VERSION", "\"${compilerPluginProject.version}\"")
}

gradlePlugin {
    plugins {
        create("testBalloonGradlePlugin") {
            id = "${project.property("local.TEST_FRAMEWORK_PLUGIN_ID")}"
            displayName = "TestBalloon compiler plugin for multiplatform test discovery and invocation"
            description = displayName
            implementationClass = "de.infix.testBalloon.GradlePlugin"
        }
    }
}

apply(from = "../build-publish-local.gradle.kts")

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
