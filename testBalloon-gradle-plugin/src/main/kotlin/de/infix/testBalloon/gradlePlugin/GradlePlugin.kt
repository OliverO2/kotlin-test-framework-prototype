package de.infix.testBalloon.gradlePlugin

import buildConfig.BuildConfig
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import kotlin.collections.iterator

@Suppress("unused")
class GradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project): Unit = with(target) {
        // WORKAROUND https://youtrack.jetbrains.com/issue/KT-53477 – KGP misses transitive compiler plugin dependencies
        dependencies.add(
            "kotlinNativeCompilerPluginClasspath",
            with(BuildConfig) {
                "$PROJECT_GROUP_ID:$PROJECT_ABSTRACTIONS_ARTIFACT_ID:$PROJECT_VERSION"
            }
        )

        extensions.create("testBalloon", GradleExtension::class.java)

        tasks.withType(Test::class.java).configureEach { testTask ->
            with(testTask) {
                // https://docs.gradle.org/current/userguide/java_testing.html
                useJUnitPlatform()

                // Ask Gradle to skip scanning for test classes. We don't need it as our compiler plugin already
                // knows. Does this make a difference? I don't know.
                isScanForTestClasses = false

                // Pass TEST_* environment variables from the Gradle invocation to the test JVM.
                for ((name, value) in System.getenv()) {
                    if (name.startsWith("TEST_")) environment(name, value)
                }

                // Pass TEST_* system properties as environment variables. NOTE: Doesn't help with K/Native.
                for ((name, value) in System.getProperties()) {
                    if (name is String && name.startsWith("TEST_")) {
                        environment(name, value)
                    }
                }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(GradleExtension::class.java)

        return project.provider {
            listOf(
                SubpluginOption(key = "debug", value = extension.debug.toString()),
                SubpluginOption(key = "jvmStandalone", value = extension.jvmStandalone.toString())
            )
        }
    }

    override fun getCompilerPluginId(): String = BuildConfig.PROJECT_COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.PROJECT_GROUP_ID,
        artifactId = BuildConfig.PROJECT_COMPILER_PLUGIN_ARTIFACT_ID,
        version = BuildConfig.PROJECT_VERSION
    )
}
