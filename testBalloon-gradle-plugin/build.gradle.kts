import buildLogic.libraryFromCatalog

plugins {
    id("buildLogic.jvm")
    id("buildLogic.publishing-jvm")
    alias(libs.plugins.com.github.gmazzo.buildconfig)
    `java-gradle-plugin`
}

description = "Gradle plugin for the TestBalloon framework"

dependencies {
    implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
    packageName("buildConfig")
    useKotlinOutput { internalVisibility = true }

    buildConfigField(
        "String",
        "PROJECT_COMPILER_PLUGIN_ID",
        "\"${project.property("local.PROJECT_COMPILER_PLUGIN_ID")}\""
    )
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
    buildConfigField("String", "PROJECT_GROUP_ID", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_COMPILER_PLUGIN_ARTIFACT_ID", "\"${projects.testBalloonCompilerPlugin.name}\"")
    buildConfigField(
        "String",
        "PROJECT_ABSTRACTIONS_ARTIFACT_ID",
        "\"${projects.testBalloonFrameworkAbstractions.name}\""
    )
    buildConfigField(
        "String",
        "PROJECT_JUNIT_PLATFORM_LAUNCHER",
        "\"${libraryFromCatalog("org.junit.platform.launcher")}\""
    )
}

gradlePlugin {
    plugins {
        create("testBalloonGradlePlugin") {
            id = "${project.property("local.PROJECT_COMPILER_PLUGIN_ID")}"
            displayName = "TestBalloon compiler plugin for multiplatform test discovery and invocation"
            description = displayName
            implementationClass = "${project.group}.gradlePlugin.GradlePlugin"
        }
    }
}
