### Run Tests

* `./gradlew clean`

* `./gradlew jvmTest`
* `./gradlew jvmTest -Pkotlin.incremental=false`

* `./gradlew jsNodeTest`
* `./gradlew jsNodeTest -Pkotlin.incremental.js=false -Pkotlin.incremental.js.klib=false -Pkotlin.incremental.js.ir=false`

* `./gradlew linuxX64Test`
* Unnecessary: `./gradlew linuxX64Test -Pkotlin.incremental.native=false`

* `./gradlew wasmJsNodeTest`
* `./gradlew wasmJsNodeTest -Pkotlin.incremental.js=false -Pkotlin.incremental.js.klib=false -Pkotlin.incremental.js.ir=false`

* `./gradlew wasmWasiNodeTest`
* `./gradlew wasmWasiNodeTest -Pkotlin.incremental.js=false -Pkotlin.incremental.js.klib=false -Pkotlin.incremental.js.ir=false`
* No effect: `./gradlew wasmWasiNodeTest -Pkotlin.incremental.wasm=false`
