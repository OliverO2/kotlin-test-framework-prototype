import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

@Suppress("unused")
class BuildLogicMultiplatformNoWasmWasiPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("buildLogic.multiplatform-base")
        }

        extensions.configure<KotlinMultiplatformExtension>("kotlin") {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
                group("common") {
                    group("nonJvm") {
                        group("jsHosted") {
                            withJs()
                            withWasmJs()
                        }
                        group("native")
                    }
                }
            }
        }
    }
}
