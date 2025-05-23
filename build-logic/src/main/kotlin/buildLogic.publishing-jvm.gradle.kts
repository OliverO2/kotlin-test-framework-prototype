import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    id("buildLogic.publishing-base")
    `java-gradle-plugin` // required for publishing
}

mavenPublishing {
    configure(GradlePlugin(JavadocJar.Empty(), sourcesJar = true))
}
