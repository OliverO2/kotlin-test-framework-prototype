import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    id("buildLogic.publishing-base")
}

mavenPublishing {
    configure(KotlinMultiplatform(JavadocJar.Empty(), sourcesJar = true))
}
