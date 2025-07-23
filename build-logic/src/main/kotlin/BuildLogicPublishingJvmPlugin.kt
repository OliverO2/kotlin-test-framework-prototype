import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class BuildLogicPublishingJvmPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("buildLogic.publishing-base")
            apply("java-gradle-plugin") // required for publishing
        }

        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            configure(GradlePlugin(JavadocJar.Empty(), sourcesJar = true))
        }
    }
}
