plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
}

kotlin {
    jvm() // To keep the Gradle KMP plugin happy
}
