package testFramework

import buildConfig.BuildConfig
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused")
class GradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("testFramework", GradleExtension::class.java)
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

    override fun getCompilerPluginId(): String = BuildConfig.TEST_FRAMEWORK_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.TEST_FRAMEWORK_COMPILER_PLUGIN_GROUP_ID,
        artifactId = BuildConfig.TEST_FRAMEWORK_COMPILER_PLUGIN_ARTIFACT_ID,
        version = BuildConfig.TEST_FRAMEWORK_COMPILER_PLUGIN_VERSION
    )
}
