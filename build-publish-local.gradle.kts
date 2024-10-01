apply(plugin = "maven-publish")

fun Project.publishing(action: PublishingExtension.() -> Unit) = configure(action)

publishing {
    repositories {
        maven {
            name = "local"
            url = uri("${System.getenv("HOME")!!}//.m2/local-repository")
        }
    }
}
