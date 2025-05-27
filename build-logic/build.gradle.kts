plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.org.jetbrains.kotlin.gradle.plugin)
    implementation(libs.org.jetbrains.kotlin.atomicfu.gradle.plugin)
    implementation(libs.org.jetbrains.kotlin.dokka.gradle.plugin)
    implementation(libs.org.jetbrains.kotlinx.kover.gradle.plugin)
    implementation(libs.org.jmailen.kotlinter.gradle.plugin)
    implementation(libs.com.vanniktech.maven.publish.gradle.plugin)
    implementation(libs.com.gradleup.compat.patrouille.gradle.plugin)
}
