plugins {
    signing
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    pom {
        name = project.name
        description = provider {
            checkNotNull(project.description) { "Project description must be set for project '${project.path}'" }
        }

        url = "https://github.com/infix-de/testBalloon/"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "infix-de"
                name = "infix Software"
                url = "https://github.com/infix-de"
            }
        }

        scm {
            url = "https://github.com/infix-de/testBalloon/"
            connection = "scm:git:git://github.com/infix-de/testBalloon.git"
            developerConnection = "scm:git:ssh://git@github.com/infix-de/testBalloon.git"
        }
    }
}

publishing {
    repositories {
        System.getProperty("user.home")?.let { home ->
            maven {
                name = "local"
                url = uri("$home/.m2/local-repository")
            }
        }
    }
}

signing.isRequired = false // not necessary for local publishing
