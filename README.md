## A Kotlin Test Framework Prototype

This prototype aims to explore a simplified Kotest framework architecture.

Features:
* A test hierarchy represented by classes `Module` – `SpecGroup` – `Spec` – `Test` – ... (`Test`s can be nested).
* Simple configuration at the `Module`, `SpecGroup`, `Spec` and `Test` level.
* The call hierarchy during execution mirrors the test hierarchy (invocations are observable via the call stack).
* Coroutine contexts mirror the test hierarchy.
* The entire framework is platform independent with almost zero redundancy.
* No indirect execution via chained lambdas (pipeline/interceptor approach), though this can be provided for user-extensibility.
 
The prototype provides only basic configuration and execution functions of a test framework. It does not provide
* actual test evaluation and reporting,
* integration with platform-specific test frameworks.

### Running Tests

* `./gradlew :application:jvmRun`
* `./gradlew :application:jsNodeRun`
* `./gradlew :application:wasmJsNodeRun`
