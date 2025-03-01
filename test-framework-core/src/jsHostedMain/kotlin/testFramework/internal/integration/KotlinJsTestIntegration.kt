package testFramework.internal.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import testFramework.Test
import testFramework.TestCompartment
import testFramework.TestElement
import testFramework.TestSession
import testFramework.TestSuite
import testFramework.internal.TestEvent
import testFramework.internal.TestReport
import testFramework.testPlatform

internal typealias JsPromiseLike = Any

/**
 * Registers [this] element with the Kotlin/JS test framework.
 */
internal fun TestElement.registerWithKotlinJsTestFramework() {
    when (this) {
        is TestSession, is TestCompartment -> {
            // Skip registering session and compartments, so that there is no pseudo-suite appearing above our
            // real top-level suites. This is required for test filtering expressions to work without wildcards.
            // Example: `TestSuite1.test1` can be found this way, otherwise we'd need to use `*TestSuite1.test1`,
            // which can be ambiguous.
            if (isEnabled) {
                childElements.forEach {
                    it.registerWithKotlinJsTestFramework()
                }
            }
        }

        is TestSuite -> {
            kotlinJsTestFramework.suite(elementName, ignored = !isEnabled) {
                childElements.forEach {
                    it.registerWithKotlinJsTestFramework()
                }
            }
        }

        is Test -> {
            kotlinJsTestFramework.test(elementName, ignored = !isEnabled) {
                TestSessionAdapter.testResult(this)
            }
        }
    }
}

/**
 * An adapter translating between our test framework and a Kotlin/JS test framework.
 *
 * Our framework needs to execute the elements of its [TestSession] in a cohesive hierarchy, preserving the
 * coroutine context nesting, hierarchical fixtures, and executing test elements according to their configuration.
 *
 * While Kotlin/JS test frameworks support structuring tests in a hierarchy, they execute each test in isolation,
 * outside any parent test element's context.
 *
 * This adapter runs the [TestSession] and the Kotlin/JS test framework concurrently, side-by-side.
 * The `TestSession` produces tests results as they become available, and provides them via
 * `Promise`s to the Kotlin/JS test framework, which picks them up asynchronously at its own pace.
 */
private object TestSessionAdapter {
    private var sessionIsExecuting = false
    private var sessionFailure: Throwable? = null
    private val testResultChannels = mutableMapOf<Test, Channel<Throwable?>>()

    init {
        check(testPlatform.parallelism == 1) {
            "${this::class.simpleName} requires a single-threaded platform," +
                " but ${testPlatform.displayName} supports ${testPlatform.parallelism} parallel threads"
        }
    }

    /**
     * Returns the result channel for [test], creating it as necessary.
     */
    private fun testResultChannel(test: Test): Channel<Throwable?> =
        testResultChannels[test] ?: Channel<Throwable?>(capacity = 1).also { testResultChannels[test] = it }

    /**
     * Returns the result of [test]'s execution.
     */
    fun testResult(test: Test): JsPromiseLike? {
        if (!sessionIsExecuting) {
            sessionIsExecuting = true
            executeTestSessionConcurrently()
        }

        // Return the `Promise` waiting for its test result to arrive from the concurrently executing `TestSession`.
        // If the entire session has failed, relay its failure with each remaining test result.
        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.testFunctionPromise {
            testResultChannel(test).receive()?.let { testFailure ->
                sessionFailure?.let { testFailure.addSuppressed(it) }
                throw testFailure
            }
            sessionFailure?.let { throw it }
        }
    }

    /**
     * Executes the [TestSession] concurrently.
     *
     * Provides results for each test in the test's own [testResultChannel] channel.
     */
    private fun executeTestSessionConcurrently() {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                TestSession.global.execute(
                    report = object : TestReport() {
                        // A TestReport relaying TestEvent.Finished to the respective test element's result channel.

                        override suspend fun add(event: TestEvent) {
                            if (event.element.isEnabled && event.element is Test && event is TestEvent.Finished) {
                                testResultChannel(event.element).send(event.throwable)
                            }
                        }
                    }
                )
            } catch (throwable: Throwable) {
                sessionFailure = throwable
            }
        }
    }
}

/**
 * Returns true if a Kotlin/JS test framework is available
 *
 * The availability depends on the platform (JS/Wasm) and runtime (browser, node).
 */
internal expect fun kotlinJsTestFrameworkAvailable(): Boolean

/**
 * The Kotlin/JS test framework (valid only if `kotlinJsTestFrameworkAvailable() == true`).
 */
internal expect val kotlinJsTestFramework: KotlinJsTestFramework

/**
 * Returns an invocation of [testFunction] as a Promise.
 *
 * As there is no common `Promise` type in kotlinx-coroutines, implementations return a target-specific type.
 * The Promise is passed to the type-agnostic JS test framework, which discovers a Promise-like object by
 * [probing for a `then` method](https://jasmine.github.io/tutorials/async).
 */
internal expect fun CoroutineScope.testFunctionPromise(testFunction: suspend () -> Unit): JsPromiseLike?

/**
 * A view of the test infrastructure API provided by the Kotlin Gradle plugins.
 *
 * API description:
 * - https://github.com/JetBrains/kotlin/blob/v1.9.23/libraries/kotlin.test/js/src/main/kotlin/kotlin/test/TestApi.kt#L38
 * NOTE: This API does not require `kotlin.test` as a dependency. It is actually provided by
 * - https://github.com/JetBrains/kotlin/tree/v1.9.23/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/testing/mocha/KotlinMocha.kt
 * - https://github.com/JetBrains/kotlin/tree/v1.9.23/libraries/tools/kotlin-test-js-runner
 *
 * Nesting of test suites may not be supported by TeamCity reporters of kotlin-test-js-runner.
 */
internal interface KotlinJsTestFramework {
    /**
     * Declares a test suite. (Theoretically, suites may be nested and may contain tests at each level.)
     *
     * [suiteFn] declares one or more tests (and/or child suites, theoretically).
     * Due to [limitations of JS test frameworks](https://github.com/mochajs/mocha/issues/2975) supported by
     * Kotlin's test infra, [suiteFn] cannot handle asynchronous invocations.
     */
    fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit)

    /**
     * Declares a test.
     *
     * [testFn] may return a `Promise`-like object for asynchronous invocation. Otherwise, the underlying JS test
     * framework will invoke [testFn] synchronously.
     */
    fun test(name: String, ignored: Boolean, testFn: () -> JsPromiseLike?)
}

/**
 * Returns the process' command line arguments if the execution environment can provide them, otherwise `null`.
 */
internal expect fun processArguments(): Array<String>?
