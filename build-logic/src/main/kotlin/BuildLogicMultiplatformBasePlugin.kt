import buildLogic.versionFromCatalog
import compat.patrouille.CompatPatrouilleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class BuildLogicMultiplatformBasePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("buildLogic.common")
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.gradleup.compat.patrouille")
            apply("org.jetbrains.kotlin.plugin.atomicfu")
            apply("org.jmailen.kotlinter")
        }

        extensions.configure<CompatPatrouilleExtension>("compatPatrouille") {
            java(project.versionFromCatalog("jdk").toInt())

            // We always stick to the same compiler version that our compiler plugin is adapted for.
            kotlin(project.versionFromCatalog("org.jetbrains.kotlin"))
        }

        extensions.configure<KotlinMultiplatformExtension>("kotlin") {
            jvm()

            js {
                nodejs()
                browser()
            }

            @OptIn(ExperimentalWasmDsl::class)
            wasmJs {
                nodejs()
                browser()
            }

            // Kotlin/Native target support – see https://kotlinlang.org/docs/native-target-support.html
            // Tier 1
            macosX64()
            macosArm64()
            iosSimulatorArm64()
            iosX64()
            iosArm64()
            // Tier 2
            linuxX64()
            linuxArm64()
            watchosSimulatorArm64()
            watchosX64()
            watchosArm32()
            watchosArm64()
            tvosSimulatorArm64()
            tvosX64()
            tvosArm64()
            // Tier 3
            androidNativeArm32()
            androidNativeArm64()
            androidNativeX86()
            androidNativeX64()
            mingwX64()
            watchosDeviceArm64()
        }
    }
}
