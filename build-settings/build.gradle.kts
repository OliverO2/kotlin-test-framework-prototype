plugins {
    `kotlin-dsl`
}

dependencies {
    // https://github.com/Splitties/refreshVersions/releases
    implementation("de.fayard.refreshVersions:de.fayard.refreshVersions.gradle.plugin:0.60.5")
    // https://github.com/gradle/foojay-toolchains/blob/main/CHANGELOG.md
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
}
