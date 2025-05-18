plugins {
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.com.github.gmazzo.buildconfig)
    `java-gradle-plugin`
    alias(libs.plugins.com.vanniktech.maven.publish)
    alias(libs.plugins.org.jmailen.kotlinter)
}

group = project.property("local.PROJECT_GROUP_ID")!!

kotlin {
    jvmToolchain(11)
}

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

publishing {
    repositories {
        maven {
            name = "local"
            url = uri("${System.getenv("HOME")!!}//.m2/local-repository")
        }
    }
}

kotlinter {
    ignoreLintFailures = false
    reporters = arrayOf("checkstyle", "plain")
}

tasks.withType<org.jmailen.gradle.kotlinter.tasks.LintTask> {
    source = source.minus(fileTree("build")).asFileTree
}

tasks.withType<org.jmailen.gradle.kotlinter.tasks.FormatTask> {
    source = source.minus(fileTree("build")).asFileTree
}
