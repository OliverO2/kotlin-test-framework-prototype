import buildLogic.versionFromCatalog
import compat.patrouille.CompatPatrouilleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class BuildLogicJvmPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("buildLogic.common")
            apply("org.jetbrains.kotlin.jvm")
            apply("com.gradleup.compat.patrouille")
        }

        extensions.configure<CompatPatrouilleExtension>("compatPatrouille") {
            java(project.versionFromCatalog("jdk").toInt())

            // We always stick to the same compiler version that our compiler plugin is adapted for.
            kotlin(project.versionFromCatalog("org.jetbrains.kotlin"))
        }
    }
}
