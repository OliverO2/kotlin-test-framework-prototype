plugins {
    id("buildLogic.multiplatform-base")
}

kotlin {
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
    //             group("native")
    //         }
    //     }
    // }

    // As a workaround for 2.2.20-Beta1, replaced with:

    applyDefaultHierarchyTemplate()

    sourceSets {
        val nonJvmMain by creating {
            dependsOn(commonMain.get())
        }

        val jsHostedMain by creating {
            dependsOn(nonJvmMain)
        }
        jsMain.get().dependsOn(jsHostedMain)
        wasmJsMain.get().dependsOn(jsHostedMain)

        nativeMain.get().dependsOn(nonJvmMain)

        val nonJvmTest by creating {
            dependsOn(commonTest.get())
        }

        val jsHostedTest by creating {
            dependsOn(nonJvmTest)
        }
        jsTest.get().dependsOn(jsHostedTest)
        wasmJsTest.get().dependsOn(jsHostedTest)

        nativeTest.get().dependsOn(nonJvmTest)
    }

    // endregion
}
