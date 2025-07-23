import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

@Suppress("unused")
class BuildLogicPublishingBasePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("signing")
            apply("com.vanniktech.maven.publish.base")
        }

        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            publishToMavenCentral()

            signAllPublications()

            pom {
                name.set(project.name)
                description.set(
                    provider {
                        checkNotNull(project.description) {
                            "Project description must be set for project '${project.path}'"
                        }
                    }
                )

                url.set("https://github.com/infix-de/testBalloon/")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("infix-de")
                        name.set("infix Software")
                        url.set("https://github.com/infix-de")
                    }
                }

                scm {
                    url.set("https://github.com/infix-de/testBalloon/")
                    connection.set("scm:git:git://github.com/infix-de/testBalloon.git")
                    developerConnection.set("scm:git:ssh://git@github.com/infix-de/testBalloon.git")
                }
            }
        }

        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                System.getProperty("user.home")?.let { home ->
                    maven {
                        name = "local"
                        url = uri("$home/.m2/local-repository")
                    }
                }

                maven {
                    name = "integrationTest"
                    url = uri(rootProject.layout.buildDirectory.dir("integration-test-repository"))
                }
            }
        }

        extensions.configure<SigningExtension>("signing") {
            isRequired = false // not necessary for local publishing
        }
    }
}
