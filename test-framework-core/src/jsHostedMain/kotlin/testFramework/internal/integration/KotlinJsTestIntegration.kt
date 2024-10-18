package testFramework.internal.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import testFramework.Test
import testFramework.TestElement
import testFramework.TestSession
import testFramework.TestSuite
import testFramework.internal.ArgumentsBasedElementSelection
import testFramework.internal.TestEvent
import testFramework.internal.TestReport
import testFramework.internal.argumentsBasedElementSelection
import testFramework.internal.configureTestsCatching

internal typealias JsPromiseLike = Any

internal suspend fun configureAndRunJsHostedTests() {
    fun TestElement.registerWithKotlinJsTestFramework() {
        when (this) {
            is Test -> {
                kotlinJsTestFramework.test(displayName, ignored = !isEnabled) {
                    TestSessionAdapter.produceTestResult(this)
                }
            }
            is TestSuite -> {
                kotlinJsTestFramework.suite(displayName, ignored = !isEnabled) {
                    childElements.forEach {
                        it.registerWithKotlinJsTestFramework()
                    }
                }
            }
        }
    }

    configureTestsCatching {
        TestSession.global.configure(
            argumentsBasedElementSelection
                ?: processArguments()?.let { ArgumentsBasedElementSelection(it) }
                ?: TestElement.AllInSelection
        )
    }.onSuccess {
        if (kotlinJsTestFrameworkAvailable()) {
            TestSession.global.registerWithKotlinJsTestFramework()
        } else {
            TestSession.global.execute(IntellijTestLog)
        }
    }
}

private object TestSessionAdapter {
    private var sessionIsExecuting = false
    private var sessionFailure: Throwable? = null
    private val testResults = mutableMapOf<Test, Channel<Throwable?>>()

    private fun testResults(test: Test): Channel<Throwable?> =
        testResults[test] ?: Channel<Throwable?>(capacity = 1).also { testResults[test] = it }

    fun produceTestResult(test: Test): JsPromiseLike? {
        if (!sessionIsExecuting) {
            // Execute the entire test session top-down, independently of the JS framework's invocations,
            // storing results for each test in its own channel.

            sessionIsExecuting = true

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    TestSession.global.execute(
                        object : TestReport() {
                            override suspend fun add(event: TestEvent) {
                                if (event.element.isEnabled && event.element is Test && event is TestEvent.Finished) {
                                    testResults(event.element).send(event.throwable)
                                }
                            }
                        }
                    )
                } catch (throwable: Throwable) {
                    sessionFailure = throwable
                }
            }
        }

        // Produce the test result, waiting for it to arrive from the concurrently executing session.
        // If the entire session has failed, relay its failure to every remaining test result.
        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.testFunctionPromise {
            testResults(test).receive()?.let { testFailure ->
                sessionFailure?.let { testFailure.addSuppressed(it) }
                throw testFailure
            }
            sessionFailure?.let { throw it }
        }
    }
}

internal expect fun kotlinJsTestFrameworkAvailable(): Boolean

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

internal expect fun processArguments(): Array<String>?
