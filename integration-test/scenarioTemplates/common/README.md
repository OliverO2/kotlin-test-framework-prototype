### Run Tests

* `./gradlew clean`

#### JVM

* `./gradlew jvmTest`
* `./gradlew jvmTest -Pkotlin.incremental=false`

With debugger:

* `gradlew -Dorg.gradle.debug=true -Pkotlin.compiler.execution.strategy=in-process jvmTest`

    > Attaching the JVM debugger to port 5005. (In IntelliJ IDEA, use the command _Run – Attach to Process_.)

#### Node/JS

* `./gradlew kotlinUpgradeYarnLock`
* `./gradlew jsNodeTest`
* `./gradlew compileTestKotlinJs`
* `./gradlew jsNodeTest -Pkotlin.incremental.js=false -Pkotlin.incremental.js.klib=false -Pkotlin.incremental.js.ir=false`

With debugger:

* `gradlew -Dorg.gradle.debug=true -Pkotlin.compiler.execution.strategy=in-process jsNodeTest`

  > Attaching the JVM debugger to port 5005. (In IntelliJ IDEA, use the command _Run – Attach to Process_.)

#### Native/Linux

* `./gradlew linuxX64Test`
* `./gradlew linuxX64Test -Pkotlin.incremental.native=false`

  > As of Kotlin 2.2.20-dev-6525, Native compilation is non-incremental by default.

#### Wasm/JS

* `./gradlew kotlinWasmUpgradeYarnLock`
* `./gradlew wasmJsNodeTest`
* `./gradlew wasmJsNodeTest -Pkotlin.incremental.js=false -Pkotlin.incremental.js.klib=false -Pkotlin.incremental.js.ir=false`

#### Wasm/WASI

* `./gradlew wasmWasiNodeTest`
* `./gradlew wasmWasiNodeTest -Pkotlin.incremental.js=false -Pkotlin.incremental.js.klib=false -Pkotlin.incremental.js.ir=false`
