## A Kotlin Test Framework Prototype

This prototype aims to explore a flexible but concise test framework architecture.

### Features

* A test hierarchy represented by nestable `TestScope`s.
    * `Test`s are TestScopes containing actual test logic with assertions, they cannot have sub-scopes.
    * `TestScopes` which are not `Test`s cannot contain test logic.
    * `TestModule`s are TestScopes used to bootstrap the framework and provide a top-level grouping.
* Coroutine contexts mirror the test hierarchy.
* The entire framework is platform independent with almost zero redundancy.
* The architecture favors simplicity and aims to avoid implicit constructs and indirection.

### Running Tests

* `./gradlew -p application-prototype cleanAllTests allTests`
* `./gradlew -p application-prototype cleanAllTests jvmTest`
* `./gradlew -p application-prototype cleanAllTests jsNodeTest`
* `./gradlew -p application-prototype cleanAllTests wasmJsNodeTest`
* `./gradlew -p application-prototype cleanAllTests linuxX64Test`

* `./gradlew -p application-prototype cleanAllTests linuxX64Test`
* `./gradlew -p application-kotest cleanAllTests linuxX64Test`

### Limitations

* Non-JVM test targets
    * must be configured with instantiated test classes,
    * visualize the test status in the test run window, but
    * do nothing more (see JVM integration below).
* Browser-based tests are not supported.
* Wasm/JS requires `@WasmExport fun startUnitTests() {}` in the Gradle module under test.
* JVM integration for IntelliJ IDEA
    * visualizes the test status in the test run window, and
    * supports class-level actions (run, debug, jump to source) from the test run window, but
    * does not support non-class scope and method-level actions from the test run window,
    * does not support running individual tests via editor gutters or the test run window,
    * does not support "rerun failed tests" (this is a limitation of the [IJ Gradle plugin](https://github.com/JetBrains/intellij-community/blob/b68794b5d030e424e4e58cfd57e9f3e08bcacac4/plugins/gradle/java/src/action/GradleRerunFailedTestsAction.kt#L89) and the [Gradle Java test task](https://github.com/gradle/gradle/issues/19897))

### What could be done

* Add an `aroundScopes` action.
* Add (a Gradle plugin with) headless browser execution for `jsBrowserTest` and `wasmJsBrowserTest`.
    * [Browser-based testing POC by Adam](https://kotlinlang.slack.com/archives/CT0G9SD7Z/p1712480969939969?thread_ts=1710849669.379249&cid=CT0G9SD7Z)
* Add a compiler plugin for automatic test generation on non-JVM targets.
* Add an IntelliJ plugin to
    * run individual tests from editor run gutters
    * support non-class scope and method-level actions from the test run window
    * support "rerun failed tests" from the test run window

### Considerations

* Use dynamic tests (on JVM via `EngineExecutionListener.dynamicTestRegistered`)
    * for `TestScope`s with test logic _and_ sub-scopes, and/or
    * for lazy configuration?
