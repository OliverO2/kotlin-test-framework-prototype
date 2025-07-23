import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class BuildLogicMultiplatformAllPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        with(pluginManager) {
            apply("buildLogic.multiplatform-base")
        }

        extensions.configure<KotlinMultiplatformExtension>("kotlin") {
            @OptIn(ExperimentalWasmDsl::class)
            wasmWasi {
                nodejs()
            }

            // region
            //
            // WORKAROUND https://youtrack.jetbrains.com/issue/KT-79131:
            //     KGP/KMP applyHierarchyTemplate regression between 2.2.20-dev-6525 and 2.2.20-Beta1

            // Formerly working up to 2.2.20-dev-6525:
            //
            // @OptIn(ExperimentalKotlinGradlePluginApi::class)
            // applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
            //     group("common") {
            //         group("nonJvm") {
            //             group("jsHosted") {
            //                 withJs()
            //                 withWasmJs()
            //             }
            //             withWasmWasi()
            //             group("native")
            //         }
            //     }
            // }

            // As a workaround for 2.2.20-Beta1, replaced with:

            applyDefaultHierarchyTemplate()

            with(sourceSets) {
                val nonJvmMain = this.create("nonJvmMain") {
                    dependsOn(commonMain.get())
                }

                val jsHostedMain = create("jsHostedMain") {
                    dependsOn(nonJvmMain)
                }
                jsMain.get().dependsOn(jsHostedMain)
                wasmJsMain.get().dependsOn(jsHostedMain)

                wasmWasiMain.get().dependsOn(nonJvmMain)
                nativeMain.get().dependsOn(nonJvmMain)

                val nonJvmTest = create("nonJvmTest") {
                    dependsOn(commonTest.get())
                }

                val jsHostedTest = create("jsHostedTest") {
                    dependsOn(nonJvmTest)
                }
                jsTest.get().dependsOn(jsHostedTest)
                wasmJsTest.get().dependsOn(jsHostedTest)

                wasmWasiTest.get().dependsOn(nonJvmTest)
                nativeTest.get().dependsOn(nonJvmTest)
            }

            // endregion
        }
    }
}
