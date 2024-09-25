## A Kotlin Test Framework Prototype

This prototype aims to explore a flexible but concise test framework architecture.

### Features

* A test hierarchy represented by nestable `TestScope`s.
    * `Test`s are test scopes containing actual test logic with assertions, they cannot have child scopes.
    * `TestSuite`s are test scopes grouping child scopes, they cannot contain test logic.
    * `TestModule`s are test suites used to bootstrap the framework and provide a top-level grouping.
* Coroutine contexts mirror the test hierarchy.
* The entire framework is platform independent with almost zero redundancy.
* The architecture favors simplicity and aims to avoid implicit constructs and indirection.

### Running Tests

* `./gradlew -p application cleanAllTests allTests`
* `./gradlew -p application cleanAllTests jvmTest`
* `./gradlew -p application cleanAllTests jsBrowserTest`
* `./gradlew -p application cleanAllTests jsNodeTest`
* `./gradlew -p application cleanAllTests wasmJsBrowserTest`
* `./gradlew -p application cleanAllTests wasmJsNodeTest`
* `./gradlew -p application cleanAllTests linuxX64Test`

* `./gradlew --continue -p application cleanAllTests jvmTest wasmJsNodeTest linuxX64Test`

### Limitations

* Non-JVM test targets
    * must be configured with instantiated test classes,
    * visualize the test status in the test run window, but
    * do nothing more (see JVM integration below).
* Test using the Kotlin/JS infra (JS/Browser, JS/Node, Wasm/JS/Browser)
    * do not report more than one level of suite nesting (intermediate levels are cut out)
    * do not execute scope-level functions and fixtures. 
* JVM integration for IntelliJ IDEA
    * visualizes the test status in the test run window, and
    * supports class-level actions (run, debug, jump to source) from the test run window, but
    * does not support non-class scope and method-level actions from the test run window,
    * does not support running individual tests via editor gutters or the test run window,
    * does not support "rerun failed tests" (this is a limitation of the [IJ Gradle plugin](https://github.com/JetBrains/intellij-community/blob/b68794b5d030e424e4e58cfd57e9f3e08bcacac4/plugins/gradle/java/src/action/GradleRerunFailedTestsAction.kt#L89) and the [Gradle Java test task](https://github.com/gradle/gradle/issues/19897))

### What could be done

* Add (a Gradle plugin with) headless browser execution for `jsBrowserTest` and `wasmJsBrowserTest`.
    * [Browser-based testing POC by Adam](https://kotlinlang.slack.com/archives/CT0G9SD7Z/p1712480969939969?thread_ts=1710849669.379249&cid=CT0G9SD7Z)
* Add a compiler plugin for automatic test discovery on non-JVM targets.
* Add an IntelliJ plugin to
    * run individual tests from editor run gutters
    * support non-class scope and method-level actions from the test run window
    * support "rerun failed tests" from the test run window

### Limitations of the Kotlin/JS test infra 

The Kotlin/JS test infrastructure delegates running tests to JS frameworks (Jasmine/Mocha/Jest), except for Wasm/JS on Node. With the JS test frameworks in control, there is currently no support for proper coroutines nesting between suites and tests.

### Considerations

* Check why only the first module is reported on JVM.
* Check whether TeamCity reporting improves missing test times on JS.
* Check whether to use @DslMarker to avoid suite functions being available in tests.
