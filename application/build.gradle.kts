import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
    alias(libs.plugins.org.jmailen.kotlinter)
}

dependencies {
    add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.testFrameworkCompilerPlugin)
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.testFrameworkCompilerPlugin)
    // WORKAROUND https://youtrack.jetbrains.com/issue/KT-53477 – KGP misses transitive compiler plugin dependencies
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.testFrameworkAbstractions)
}

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
        mainRun {
            mainClass = "MainKt"
        }
        compilerOptions {
            freeCompilerArgs = listOf("-Xjdk-release=$jdkVersion")
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

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        // freeCompilerArgs = listOf("-P", "plugin:com.example.testFramework:debug=true")
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        group("common") {
            group("jsHosted") {
                withJs()
                withWasmJs()
            }
        }
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(projects.testFrameworkCore)
                implementation(libs.org.jetbrains.kotlinx.atomicfu)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.org.jetbrains.kotlinx.coroutines.debug)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // https://docs.gradle.org/current/userguide/java_testing.html
    useJUnitPlatform()

    // Pass TEST_* environment variables from the Gradle invocation to the test JVM.
    for ((name, value) in System.getenv()) {
        if (name.startsWith("TEST_")) environment(name, value)
    }

    // Pass application.test.* system properties from the Gradle invocation to the test JVM.
    // Pass TEST_* system properties as environment variables. NOTE: Doesn't help with K/Native.
    for ((name, value) in System.getProperties()) {
        when {
            name !is String -> Unit
            name.startsWith("application.test.") -> systemProperty(name, value)
            name.startsWith("TEST_") -> environment(name, value)
        }
    }
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
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
