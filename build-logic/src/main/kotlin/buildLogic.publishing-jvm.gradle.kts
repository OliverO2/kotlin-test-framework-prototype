import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    id("buildLogic.publishing-base")
}

mavenPublishing {
    configure(KotlinJvm(JavadocJar.Empty(), sourcesJar = true))
}
