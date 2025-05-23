plugins {
    id("buildLogic.jvm")
    id("buildLogic.publishing-jvm")
    alias(libs.plugins.com.github.gmazzo.buildconfig)
}

description = "Compiler plugin for the TestBalloon framework"

dependencies {
    implementation(projects.testBalloonFrameworkAbstractions)
    implementation(kotlin("compiler-embeddable"))
    implementation(libs.org.jetbrains.kotlinx.coroutines.core)

    testImplementation(libs.dev.zacsweers.kctfork)
    testImplementation(kotlin("test"))
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
    buildConfigField("String", "PROJECT_FRAMEWORK_CORE_ARTIFACT_ID", "\"${projects.testBalloonFrameworkCore.name}\"")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
