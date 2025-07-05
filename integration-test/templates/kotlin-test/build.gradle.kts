import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform") version "{{version:org.jetbrains.kotlin}}"
    id("com.gradleup.compat.patrouille") version "{{version:com.gradleup.compat.patrouille}}"
    id("de.infix.testBalloon") version "{{prop:version}}"
}

compatPatrouille {
    java("{{version:jdk}}".toInt())
    kotlin("{{version:org.jetbrains.kotlin}}")
}

kotlin {
    jvm()

    js {
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    // Kotlin/Native target support – see https://kotlinlang.org/docs/native-target-support.html
    // Tier 1
    // macosX64()
    // macosArm64()
    // iosSimulatorArm64()
    // iosX64()
    // iosArm64()
    // Tier 2
    linuxX64()
    // linuxArm64()
    // watchosSimulatorArm64()
    // watchosX64()
    // watchosArm32()
    // watchosArm64()
    // tvosSimulatorArm64()
    // tvosX64()
    // tvosArm64()
    // Tier 3
    // androidNativeArm32()
    // androidNativeArm64()
    // androidNativeX86()
    // androidNativeX64()
    // mingwX64()
    // watchosDeviceArm64()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks {
    register("listTests") {
        group = "verification"

        val testTaskNames = project.tasks.mapNotNull { task ->
            task.takeIf { it.name.endsWith("Test") && !it.javaClass.name.contains("Report") }?.name
        }

        doLast {
            println(testTaskNames.joinToString("\n"))
        }
    }

    withType<Test> {
        testLogging { showStandardStreams = true }
    }
    withType<KotlinJsTest> {
        testLogging { showStandardStreams = true }
    }
    withType<KotlinNativeTest> {
        testLogging { showStandardStreams = true }
    }
}
