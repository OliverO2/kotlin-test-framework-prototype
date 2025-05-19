package buildLogic

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

/**
 * Adds the configuration normally supplied by the project's own Gradle plugin.
 *
 * This enables the project's compiler plugin without loading it from a repository.
 */
fun Project.addTestBalloonPluginFromProject(compilerPluginDependency: Dependency, abstractionsDependency: Dependency) {
    dependencies {
        add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, compilerPluginDependency)
        add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, compilerPluginDependency)
        // WORKAROUND https://youtrack.jetbrains.com/issue/KT-53477 – KGP misses transitive compiler plugin dependencies
        add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, abstractionsDependency)
    }

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
}
