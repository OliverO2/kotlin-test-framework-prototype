import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jmailen.kotlinter)
    // alias(libs.plugins.de.infix.testBalloon) // Use this outside of this project
}

// region In-project configuration normally supplied by the framework's own Gradle plugin
dependencies {
    add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.compilerPlugin)
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.compilerPlugin)
    // WORKAROUND https://youtrack.jetbrains.com/issue/KT-53477 – KGP misses transitive compiler plugin dependencies
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.frameworkAbstractions)
}
// endregion

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

    // @OptIn(ExperimentalWasmDsl::class)
    // wasmWasi {
    //     binaries.executable()
    //     nodejs()
    // }

    linuxX64 {
        binaries.executable()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        // freeCompilerArgs.addAll("-P", "plugin:de.infix.testBalloon:debug=true")
    }

    sourceSets {
        commonTest {
            dependencies {
                // implementation(libs.de.infix.testBalloon.integrations.kotest.assertions) // Use this outside of this project
                implementation(projects.integrationsKotestAssertions)
            }
        }
    }
}

// region In-project configuration normally supplied by the framework's own Gradle plugin
// This section should be omitted in production build scripts in favor of the framework's Gradle plugin.
tasks.withType<Test>().configureEach {
    // https://docs.gradle.org/current/userguide/java_testing.html
    useJUnitPlatform()

    // Ask Gradle to skip scanning for test classes. We don't need it as our compiler plugin already
    // knows. Caveat: There is probably a bug in Gradle, making it scan anyway.
    isScanForTestClasses = false

    // Pass TEST_* environment variables from the Gradle invocation to the test JVM.
    for ((name, value) in System.getenv()) {
        if (name.startsWith("TEST_")) environment(name, value)
    }

    // Pass application.test.* system properties from the Gradle invocation to the test JVM.
    // Pass TEST_* system properties as environment variables. NOTE: Doesn't help with K/Native.
    for ((name, value) in System.getProperties()) {
        when {
            name !is String -> Unit
            name.startsWith("TEST_") -> environment(name, value)
        }
    }
}
// endregion

tasks {
    val orderedTaskPrefixes =
        listOf(
            "jvm",
            "jsNode",
            "jsBrowser",
            "wasmJsNode",
            "wasmJsBrowser",
            // "wasmWasiNode",
            "linuxX64"
        )
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
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
