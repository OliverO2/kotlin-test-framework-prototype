## A Kotlin Test Framework Prototype

This prototype aims to explore a flexible but concise test framework architecture.

### Features

* A test hierarchy represented by nestable `TestScope`s.
    * `Test`s are test scopes containing actual test logic with assertions, they cannot have child scopes.
    * `TestSuite`s are test scopes grouping child scopes, they cannot contain test logic.
    * A `TestSession` is the root of the test hierarchy for an individual test run.
* Tests are fully coroutine-aware, coroutine contexts mirror the suites/tests hierarchy.
* Suites support lazily initialized test fixtures with `AutoCloseable` support. Fixture initialization and tear-down (auto-close) functions can suspend.
* Suites support a suspending `aroundAll` action.
* Suite nesting and suspending suite-level actions are fully supported on JavaScript engines, using the Kotlin/JS test infra.
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

### Limitations

* Non-JVM test targets must be configured with instantiated test classes.
* Tests using the Kotlin/JS infra (JS/Browser, JS/Node, Wasm/JS/Browser) do not report more than one level of suite nesting (intermediate levels are cut out).
* Integration for IntelliJ IDEA
    * supports class-level actions (run, debug, jump to source) from the test run window for JVM tests only,
    * does not support non-class scope and method-level actions from the test run window,
    * does not support running individual tests via editor gutters or the test run window,
    * does not support "rerun failed tests" (this is a limitation of the [IJ Gradle plugin](https://github.com/JetBrains/intellij-community/blob/b68794b5d030e424e4e58cfd57e9f3e08bcacac4/plugins/gradle/java/src/action/GradleRerunFailedTestsAction.kt#L89) and the [Gradle Java test task](https://github.com/gradle/gradle/issues/19897))

### What could be done

* Add a compiler plugin for automatic test discovery on non-JVM targets.
* Add an IntelliJ plugin to
    * run individual tests from editor run gutters
    * support non-class scope and method-level actions from the test run window
    * support "rerun failed tests" from the test run window

### Considerations

* Combine sequential execution and parallelism into one settings class.
* Check whether to use @DslMarker to avoid suite functions being available in tests.

### IDE and Build Tool Interoperability

#### JUnit Platform

##### General

[Test Engines](https://junit.org/junit5/docs/current/user-guide/#test-engines) must
* discover tests from an [`EngineDiscoveryRequest`](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/EngineDiscoveryRequest.html), yielding a tree of test descriptors, and
* execute tests according to an [`ExecutionRequest`](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/ExecutionRequest.html), starting at the root test descriptor and observing tests events via a listener (which also observes the discovery of new tests, if these are dynamically registered during execution).

A [test descriptor](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/TestDescriptor.html) describes a node in the test tree (a suite or a test). Each node has a [unique identifier](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/UniqueId.html), comprised of a list of type/value segments. Segment type and value are non-empty strings. Typical segment types are "test", "class", "engine", but there appears to be no defined scheme and "engine" [seems to be the only stable name](https://github.com/junit-team/junit5/discussions/3551).

##### Test Discovery

An [`EngineDiscoveryRequest`](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/EngineDiscoveryRequest.html)
* selects test nodes via
    * ClassSelector: named class,
    * MethodSelector: named method (plus optional signature),
    * UniqueIdSelector: unique identifier,
    * [others, e.g.](https://junit.org/junit5/docs/current/api/org.junit.platform.engine/org/junit/platform/engine/DiscoverySelector.html): classpath, directory, file, module, package, URI.
* filters test nodes via
    * ClassNameFilter: list of class name RE patterns to include and/or exclude
    * PackageNameFilter: list of package name RE patterns to include and/or exclude
 
The [Gradle test task](https://docs.gradle.org/current/userguide/java_testing.html) uses
* these selectors:
    * ClassSelector (Gradle passes a selector for every class it finds, not knowing which ones are test classes)
    * UniqueIdSelector (Gradle Enterprise: distribute tests across processes)
* these filters:
    * ClassNameFilter (if [test filtering](https://docs.gradle.org/current/userguide/java_testing.html#test_filtering) is used)
    
Kotest supports
* these selectors:
    * PackageSelector
    * ClassSelector
    * UniqueIdSelector (added by a Gradle member to support distributing tests across processes in Gradle Enterprise)
* these filters:
    * ClassNameFilter
    * PackageNameFilter

##### Source Code Test Discovery (IDE Support)

[The Testable annotation](https://junit.org/junit5/docs/current/api/org.junit.platform.commons/org/junit/platform/commons/annotation/Testable.html) exists to make IDEs aware of elements which can be executed as a test or test container. It is intended for use cases where full discovery via compiled code is unavailable. (IntelliJ IDEA [contains some support](https://github.com/JetBrains/intellij-community/blob/65cf881f35eea8a594b9375651a7a03823f09723/java/execution/impl/src/com/intellij/execution/junit/JUnitUtil.java#L42) for it. Is this is actually used for Kotlin?) 

#### IntelliJ IDEA

The IDE runs tests via regular Gradle invocations.

When selecting a single test in the run window, the IDE runs it via a `--tests` filter, e.g. `--tests "com.example.TestSuite2"`, if
* the test ran via JUnit Platform, or
* the IntelliJ XML log contains a `descriptor` tag with a `classname` attribute supplying a fully qualified class name _and_ the test run respects the `--tests` filter.

The IDE [re-runs failed tests](https://github.com/JetBrains/intellij-community/blob/8032aef848d1edf5771e442cb749e047b885876c/plugins/gradle/java/src/action/GradleRerunFailedTestsAction.kt) by analyzing the test files' source code and [creating a Gradle invocation with filters](https://github.com/JetBrains/intellij-community/blob/8032aef848d1edf5771e442cb749e047b885876c/plugins/gradle/java/src/execution/test/runner/TestGradleConfigurationProducerUtil.kt#L15).
