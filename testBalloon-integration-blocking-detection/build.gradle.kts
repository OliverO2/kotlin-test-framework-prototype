import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.com.vanniktech.maven.publish)
    alias(libs.plugins.org.jmailen.kotlinter)
}

// region In-project configuration normally supplied by the framework's own Gradle plugin
dependencies {
    add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.testBalloonCompilerPlugin)
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.testBalloonCompilerPlugin)
    // WORKAROUND https://youtrack.jetbrains.com/issue/KT-53477 – KGP misses transitive compiler plugin dependencies
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.testBalloonFrameworkAbstractions)
}
// endregion

group = project.property("local.PROJECT_GROUP_ID")!!

val jdkVersion = project.property("local.jdk.version").toString().toInt()

kotlin {
    jvmToolchain(jdkVersion)

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        // freeCompilerArgs.addAll("-P", "plugin:de.infix.testBalloon:debug=true")
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjdk-release=$jdkVersion")
        }
    }

    js {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    linuxX64 {
        binaries.executable()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        group("common") {
            group("nonJvm") {
                withJs()
                withWasmJs()
                withWasmWasi()
                withLinux()
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.testBalloonFrameworkCore)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test")) // for assertions only
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.io.projectreactor.tools.blockhound)
                implementation(libs.org.jetbrains.kotlinx.coroutines.debug)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            val javaLauncher = javaLauncher.orNull
            buildList {
                if (javaLauncher != null && javaLauncher.metadata.languageVersion >= JavaLanguageVersion.of(16)) {
                    // https://github.com/reactor/BlockHound/issues/33
                    add("-XX:+AllowRedefinitionToAddDeleteMethods")
                }
            }
        }
    )
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
