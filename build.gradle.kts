plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
}

kotlin {
    jvm() // To keep the Gradle KMP plugin happy
}

tasks {
    for (type in listOf("allTests", "jvmTest")) {
        register("prePublishingTests${type.capitalize()}") {
            group = "verification"

            dependsOn(":testBalloon-compiler-plugin:$type")
            dependsOn(":testBalloon-gradle-plugin:test")
            dependsOn(":testBalloon-framework-core:$type")

            dependsOn(":testBalloon-integration-kotest-assertions:$type")
            dependsOn(":testBalloon-integration-blocking-detection:$type")
        }
    }

    register<Exec>("retryTestsUntilFailure") {
        group = "verification"
        commandLine = listOf("/bin/bash", "-c", "while gradlew -p framework-core cleanAllTests allTests; do true; done")
    }
}
