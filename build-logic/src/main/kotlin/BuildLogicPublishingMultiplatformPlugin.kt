import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class BuildLogicPublishingMultiplatformPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("buildLogic.publishing-base")
        }

        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            configure(KotlinMultiplatform(JavadocJar.Empty(), sourcesJar = true))
        }
    }
}
