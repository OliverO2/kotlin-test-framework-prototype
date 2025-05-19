import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    id("buildLogic.multiplatform-base")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        group("common") {
            group("nonJvm") {
                group("jsHosted") {
                    withJs()
                    withWasmJs()
                }
                group("native")
            }
        }
    }
}
