plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.sam.with.receiver") version "2.2.0"
    kotlin("plugin.assignment") version "2.2.0"
    `java-gradle-plugin`
}

dependencies {
    // https://github.com/Splitties/refreshVersions/releases
    implementation("de.fayard.refreshVersions:de.fayard.refreshVersions.gradle.plugin:0.60.5")
    // https://github.com/gradle/foojay-toolchains/blob/main/CHANGELOG.md
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
}

samWithReceiver {
    annotation(HasImplicitReceiver::class.qualifiedName!!)
}

assignment {
    annotation(SupportsKotlinAssignmentOverloading::class.qualifiedName!!)
}

gradlePlugin {
    plugins {
        register("buildSettings") {
            id = "buildSettings"
            implementationClass = "BuildSettingsPlugin"
        }
    }
}
