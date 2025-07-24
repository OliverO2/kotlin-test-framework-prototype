import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.refreshVersions
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

@Suppress("unused")
class BuildSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) = with(target) {
        pluginManager.apply("de.fayard.refreshVersions")
        pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")

        dependencyResolutionManagement {
            pluginManagement {
                repositories {
                    projectRepositories()
                }
            }
            @Suppress("UnstableApiUsage")
            repositories {
                projectRepositories()
            }
        }

        refreshVersions {
            featureFlags {
                enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
            }
        }

        enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
    }

    private fun RepositoryHandler.projectRepositories() {
        mavenCentral()
        System.getProperty("user.home")?.let { home ->
            maven(url = uri("$home/.m2/local-repository"))
        }
        maven(url = uri("https://redirector.kotlinlang.org/maven/dev"))
        gradlePluginPortal()
    }

    private fun RepositoryHandler.maven(url: URI) {
        maven {
            this.url = url
        }
    }

    private fun uri(pathOrUri: String) = URI(
        if (pathOrUri.matches(uriSchemeRegex)) {
            pathOrUri
        } else {
            "file://${Path(pathOrUri).invariantSeparatorsPathString}"
        }
    )

    private val uriSchemeRegex = Regex("""^\w+://.*""")
}
