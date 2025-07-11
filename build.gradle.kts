import org.gradle.kotlin.dsl.support.uppercaseFirstChar

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform) apply false
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.com.vanniktech.maven.publish) apply false
}

tasks {
    for (type in listOf("allTests", "jvmTest")) {
        register("prePublishingTests${type.uppercaseFirstChar()}") {
            group = "verification"

            dependsOn(":testBalloon-compiler-plugin:test")
            dependsOn(":testBalloon-gradle-plugin:test")
            dependsOn(":testBalloon-framework-core:$type")

            dependsOn(":integration-test:test")

            dependsOn(":testBalloon-integration-kotest-assertions:$type")
            dependsOn(":testBalloon-integration-blocking-detection:$type")
        }
    }

    register<Exec>("retryTestsUntilFailure") {
        group = "verification"
        commandLine = listOf("/bin/bash", "-c", "while gradlew -p framework-core cleanAllTests allTests; do true; done")
    }
}
