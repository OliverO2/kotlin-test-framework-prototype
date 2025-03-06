package testFramework.internal.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.Test
import testFramework.TestCompartment
import testFramework.TestElement
import testFramework.TestSession
import testFramework.TestSuite
import testFramework.internal.TestElementEvent
import testFramework.internal.TestReport
import testFramework.internal.logError
import testFramework.internal.logInfo
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
                TestSessionRelay.resultReceivingPromise(this)
            }
        }
    }
}

/**
 * A relay transmitting results from a concurrently executing [TestSession] to a Kotlin/JS test framework.
 *
 * The [TestSession] executes its elements in a cohesive hierarchy,
 * - preserving the coroutine context nesting,
 * - supporting hierarchical fixtures, and
 * - executing test elements according to their configuration.
 *
 * While Kotlin/JS test frameworks support structuring tests in a hierarchy, they execute each test in isolation,
 * outside any parent test element's context.
 *
 * This relay executes the [TestSession] concurrently with the Kotlin/JS test framework controlled externally.
 * It relays `TestSession` results to result-receiving promises, which have been registered for each test in
 * the Kotlin/JS test framework.
 */
private object TestSessionRelay {
    private var sessionJob: Job? = null
    private val resultChannels = mutableMapOf<Test, Channel<Throwable?>>()

    init {
        check(testPlatform.parallelism == 1) {
            "${this::class.simpleName} requires a single-threaded platform," +
                " but ${testPlatform.displayName} supports ${testPlatform.parallelism} parallel threads"
        }
    }

    /**
     * Returns the result channel for [this] test, creating it as necessary.
     */
    private fun Test.resultChannel(): Channel<Throwable?> = resultChannels[this]
        ?: Channel<Throwable?>(capacity = 1).also { resultChannels[this] = it }

    /**
     * Returns a result-receiving Promise for [test].
     */
    fun resultReceivingPromise(test: Test): JsPromiseLike? {
        if (sessionJob == null) {
            @OptIn(DelicateCoroutinesApi::class)
            sessionJob = GlobalScope.launchedSession()
        }

        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.testFunctionPromise {
            val testFailure = try {
                test.resultChannel().receive() // regular test result
            } catch (externalFailure: Throwable) {
                externalFailure // suite or session failure
            }
            resultChannels.remove(test)
            if (testFailure != null) {
                throw testFailure
            }
        }
    }

    /**
     * Executes the [TestSession] concurrently.
     *
     * Sends results for each test in the test's own [Test.resultChannel]. Takes care of sending failures outside
     * tests to all potentially affected tests.
     */
    private fun CoroutineScope.launchedSession(): Job = launch {
        try {
            println("Session start")
            TestSession.global.execute(
                report = object : TestReport() {
                    // A TestReport relaying test results to the corresponding test elements' result channel(s).

                    override suspend fun add(event: TestElementEvent) {
                        if (event.element.isEnabled && event is TestElementEvent.Finished) {
                            event.sendResult()
                        }
                    }
                }
            )
        } catch (throwable: Throwable) {
            withContext(NonCancellable) {
                sendSessionFailure(throwable)
            }
        }
    }

    /**
     * Sends the [TestElementEvent.Finished] event's result to the corresponding result channel(s).
     *
     * - Sends a single test result to the test's result channel.
     * - Sends a suite failure as a close cause to the result channels of all tests under the suite. Reasons:
     *   - A suite failure occurs outside a single test, but results are only relayed for tests.
     *     Therefore, the suite failure must be relayed to affected tests.
     *   - Since we do not know which tests have already completed and which will complete in the future,
     *     we must relay the failure to all tests under the suite.
     */
    private suspend fun TestElementEvent.Finished.sendResult() {
        when (element) {
            is Test -> {
                val channel = element.resultChannel()
                channel.send(throwable)
                channel.close()
            }

            is TestSuite -> {
                if (throwable != null) {
                    element.forEachChildTreeElement { childElement ->
                        if (childElement is Test) {
                            childElement.resultChannel().close(throwable)
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends the session failure [throwable] to all test result channels.
     */
    private suspend fun sendSessionFailure(throwable: Throwable) {
        TestSession.global.forEachChildTreeElement { childElement ->
            if (childElement is Test) {
                if (childElement.resultChannel().close(throwable)) {
                    logInfo { "$childElement: Aborting result reporting: $throwable." }
                }
            }
        }
        logError { "Aborting result reporting: $throwable." }
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
