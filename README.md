## A Kotlin Test Framework Prototype

This prototype aims to explore a simplified test framework architecture.

Features:
* A test hierarchy represented by nestable `TestScope`s. (`TestModule`s are TestScopes used to bootstrap the framework.)
* TestScopes can contain assertions or sub-scopes.
* The call hierarchy during execution mirrors the test hierarchy (invocations are observable via the call stack).
* Coroutine contexts mirror the test hierarchy.
* The entire framework is platform independent with almost zero redundancy.
* Less indirect execution via chained lambdas (pipeline/interceptor approach), though more can be provided for user-extensibility.
 
The prototype provides only basic configuration and execution functions of a test framework. It does not provide
* actual test evaluation and reporting,
* integration with platform-specific test frameworks.

### Running Tests

* `./gradlew :application:jvmRun`
* `./gradlew :application:jsNodeRun`
* `./gradlew :application:wasmJsNodeRun`

#### Results

``` 
TestModule.Default.TestScope1: beforeFirstScope TestScope1
TestModule.Default.TestScope1: beforeEachScope TestScope1
TestModule.Default.TestScope1: aroundEachScope TestScope1 start
TestModule.Default.TestScope1.test1: in TestScope1.test1 [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1: aroundEachScope TestScope1 end
TestModule.Default.TestScope1: afterEachScope TestScope1
TestModule.Default.TestScope1: beforeEachScope TestScope1
TestModule.Default.TestScope1: aroundEachScope TestScope1 start
TestModule.Default.TestScope1.test2(#1/2): in TestScope1.test2 [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1.test2(#1/2): beforeEachScope TestScope1.test2
TestModule.Default.TestScope1.test2.nested: in TestScope1.test2.nested [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1.test2(#2/2): in TestScope1.test2 [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1.test2(#2/2): beforeEachScope TestScope1.test2
TestModule.Default.TestScope1.test2.nested: in TestScope1.test2.nested [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1: aroundEachScope TestScope1 end
TestModule.Default.TestScope1: afterEachScope TestScope1
TestModule.Default.TestScope1: beforeEachScope TestScope1
TestModule.Default.TestScope1: aroundEachScope TestScope1 start
TestModule.Default.TestScope1.test3.1: in TestScope1.test3.1 [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1: aroundEachScope TestScope1 end
TestModule.Default.TestScope1: afterEachScope TestScope1
TestModule.Default.TestScope1: beforeEachScope TestScope1
TestModule.Default.TestScope1: aroundEachScope TestScope1 start
TestModule.Default.TestScope1.test3.2: in TestScope1.test3.2 [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1: aroundEachScope TestScope1 end
TestModule.Default.TestScope1: afterEachScope TestScope1
TestModule.Default.TestScope1: beforeEachScope TestScope1
TestModule.Default.TestScope1: aroundEachScope TestScope1 start
TestModule.Default.TestScope1.test3.3: in TestScope1.test3.3 [CoroutineName(aroundEachScope TestScope1)]
TestModule.Default.TestScope1: aroundEachScope TestScope1 end
TestModule.Default.TestScope1: afterEachScope TestScope1
TestModule.Default.TestScope1: afterLastScope TestScope1
TestModule.Default.TestScope2: beforeEachScope TestScope2
TestModule.Default.TestScope2.test2(timeout=2s): in TestScope2.test2 – before delay
TestModule.Default.TestScope2.test2(timeout=2s): in TestScope2.test2 – after delay
TestModule.Default.TestScope2: afterLastScope TestScope2
TestModule.SingleThreaded.TestScope3: beforeEachScope TestScope3
TestModule.SingleThreaded.TestScope3.test2: in TestScope3.test2 – before delay
TestModule.SingleThreaded.TestScope3.test2: in TestScope3.test2 – after delay
TestModule.SingleThreaded.TestScope3: afterLastScope TestScope3
```

### Considerations

* Combining test execution and configuration (for the same scope or sub-scopes) means that each scope must be re-built for each invocation (in an effectively idempotent way) in order to avoid multiple additions of sub-scopes.
* In order to schedule higher-level `around`... invocations for disabled and repeated tests, the complete test tree must be built before trying to execute it. The "failgood" runner calls this a "test plan".
