import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jmailen.kotlinter)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjdk-release=$jdkVersion")
        }
    }

    js {
        binaries.executable()
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        binaries.executable()
        nodejs()
    }

    linuxX64 {
        binaries.executable()
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // https://docs.gradle.org/current/userguide/java_testing.html
    useJUnitPlatform()
}

tasks {
    val orderedTaskPrefixes =
        listOf("jvm", "jsNode", "jsBrowser", "wasmJsNode", "wasmJsBrowser", "wasmWasiNode", "linuxX64")
    var lastTaskName: String? = null
    orderedTaskPrefixes.forEach {
        val taskName = "${it}Test"
        lastTaskName?.let {
            named(taskName).configure {
                mustRunAfter(it)
            }
        }
        lastTaskName = taskName
    }
}

kotlinter {
    ignoreLintFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
