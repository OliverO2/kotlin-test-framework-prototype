plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.sam.with.receiver") version "2.2.0"
    kotlin("plugin.assignment") version "2.2.0"
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.org.jetbrains.kotlin.gradle.plugin)
    implementation(libs.org.jetbrains.kotlin.atomicfu.gradle.plugin)
    implementation(libs.org.jetbrains.kotlin.dokka.gradle.plugin)
    implementation(libs.org.jetbrains.kotlinx.kover.gradle.plugin)
    implementation(libs.org.jmailen.kotlinter.gradle.plugin)
    implementation(libs.com.vanniktech.maven.publish.gradle.plugin)
    implementation(libs.com.gradleup.compat.patrouille.gradle.plugin)
}

samWithReceiver {
    annotation(HasImplicitReceiver::class.qualifiedName!!)
}

assignment {
    annotation(SupportsKotlinAssignmentOverloading::class.qualifiedName!!)
}

gradlePlugin {
    plugins {
        val pluginMap = mapOf(
            "buildLogic.common" to "BuildLogicCommonPlugin",
            "buildLogic.jvm" to "BuildLogicJvmPlugin",
            "buildLogic.multiplatform-base" to "BuildLogicMultiplatformBasePlugin",
            "buildLogic.multiplatform-all" to "BuildLogicMultiplatformAllPlugin",
            "buildLogic.multiplatform-noWasmWasi" to "BuildLogicMultiplatformNoWasmWasiPlugin",
            "buildLogic.publishing-base" to "BuildLogicPublishingBasePlugin",
            "buildLogic.publishing-jvm" to "BuildLogicPublishingJvmPlugin",
            "buildLogic.publishing-multiplatform" to "BuildLogicPublishingMultiplatformPlugin"
        )

        for ((id, implementationClass) in pluginMap) {
            register(id) {
                this.id = id
                this.implementationClass = implementationClass
            }
        }
    }
}
