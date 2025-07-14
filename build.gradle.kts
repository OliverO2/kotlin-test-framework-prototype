plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform) apply false
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.com.vanniktech.maven.publish) apply false
}

tasks {
    for ((targetSubSet, kmpTaskName) in mapOf("AllTargets" to "allTests", "JvmOnly" to "jvmTest")) {
        register("componentTests$targetSubSet") {
            group = "verification"

            dependsOn(":testBalloon-compiler-plugin:test")
            dependsOn(":testBalloon-gradle-plugin:test")
            dependsOn(":testBalloon-framework-core:$kmpTaskName")

            dependsOn(":testBalloon-integration-kotest-assertions:$kmpTaskName")
            dependsOn(":testBalloon-integration-blocking-detection:$kmpTaskName")
        }
    }

    register("integrationTests") {
        group = "verification"

        dependsOn(":integration-test:test")
    }
}
