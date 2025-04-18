package de.infix.testBalloon.framework

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A test element's configuration, modifying the execution of tests and suites.
 *
 * A test element can be configured via any number of chained [TestConfig]s.
 *
 * When [TestConfig]s are combined into a chain,
 * - a later conflicting [TestConfig] takes precedence over an earlier one,
 * - non-conflicting coroutine context elements accumulate,
 * - a series of [aroundAll], [aroundEach] wrappers, or [traversal]s nest from the outside to the inside in order
 *   of appearance.
 *
 * Each [TestConfig] operates at the [TestElement] level where it is configured. However, child elements inherit
 * some configuration effects as described in the respective [TestConfig] function.
 *
 * Use an existing configuration or the empty [TestConfig] object as the starting point, then invoke the [TestConfig]
 * functions to return chained configurations.
 *
 * Example:
 * ```
 * configuration = TestConfig
 *     .coroutineContext(UnconfinedTestDispatcher())
 *     .invocation(TestInvocation.CONCURRENT)
 * ```
 */
open class TestConfig internal constructor(
    private val parameterizingAction: ParameterizingAction?,
    private val executionWrappingAction: ExecutionWrappingAction?,
    private val reportSetupAction: ReportSetupAction?
) {
    /** Returns a [TestConfig] which combines `this` configuration with a parameterizing action. */
    internal fun parameterizing(nextParameterizingAction: ParameterizingAction): TestConfig = TestConfig(
        parameterizingAction = if (parameterizingAction != null) {
            {
                parameterizingAction()
                nextParameterizingAction()
            }
        } else {
            nextParameterizingAction
        },
        executionWrappingAction = executionWrappingAction,
        reportSetupAction = reportSetupAction
    )

    /** Returns a [TestConfig] which combines `this` configuration with an execution-wrapping action. */
    internal fun executionWrapping(innerExecutionWrappingAction: ExecutionWrappingAction): TestConfig = TestConfig(
        parameterizingAction = parameterizingAction,
        executionWrappingAction = if (executionWrappingAction != null) {
            { elementAction ->
                executionWrappingAction {
                    innerExecutionWrappingAction(elementAction)
                }
            }
        } else {
            innerExecutionWrappingAction
        },
        reportSetupAction = reportSetupAction
    )

    /** Returns a [TestConfig] which combines `this` configuration with a report setup action. */
    internal fun reportSetup(nextReportSetupAction: ReportSetupAction): TestConfig = TestConfig(
        parameterizingAction = parameterizingAction,
        executionWrappingAction = executionWrappingAction,
        reportSetupAction = if (reportSetupAction != null) {
            { elementAction ->
                reportSetupAction(elementAction)
                nextReportSetupAction(elementAction)
            }
        } else {
            nextReportSetupAction
        }
    )

    /** Returns a [TestConfig] which chains `this` configuration with [otherConfiguration]. */
    fun chainedWith(otherConfiguration: TestConfig): TestConfig {
        var result = this

        otherConfiguration.parameterizingAction?.let { result = result.parameterizing(it) }
        otherConfiguration.executionWrappingAction?.let { result = result.executionWrapping(it) }
        otherConfiguration.reportSetupAction?.let { result = result.reportSetup(it) }

        return result
    }

    /** Parameterizes [testElement] according to `this` configuration. */
    internal open fun parameterize(testElement: TestElement) {
        if (parameterizingAction != null) testElement.parameterizingAction()
    }

    /** Wraps the execution according to `this` configuration, then executes [elementAction] on [testElement]. */
    internal suspend fun <SpecificTestElement : TestElement> executeWrapped(
        testElement: SpecificTestElement,
        elementAction: suspend SpecificTestElement.() -> Unit
    ) {
        val executionTraversalContext = ExecutionTraversalContext.current()
        if (executionTraversalContext != null) {
            executionTraversalContext.executeInside(testElement) {
                executionWrappingAction.wrapIfNotNull(testElement, elementAction)
            }
        } else {
            executionWrappingAction.wrapIfNotNull(testElement, elementAction)
        }
    }

    internal suspend fun withReportSetup(
        testElement: TestElement,
        reportingAction: suspend (additionalReports: Iterable<TestReport>?) -> Unit
    ) {
        if (reportSetupAction != null) {
            reportSetupAction(testElement) {
                reportingAction(ReportContext.additionalReports())
            }
        } else {
            reportingAction(ReportContext.additionalReports())
        }
    }

    /** The initial (empty) test configuration. */
    companion object : TestConfig(null, null, null)
}

private typealias ParameterizingAction = TestElement.() -> Unit
private typealias ReportSetupAction = suspend TestElement.(elementAction: suspend TestElement.() -> Unit) -> Unit

/**
 * An action wrapping the execution for a [TestElement].
 *
 * `elementAction` can be the element's primary action, or a cumulative action, which includes wrapping actions,
 * plus the elements primary action.
 *
 * Requirements:
 * - An [ExecutionWrappingAction] must invoke `elementAction` exactly once.
 *
 * Requirements for [TestElement]s of type [Test]:
 * - If `elementAction` throws, it is considered a test failure. If [ExecutionWrappingAction] catches the exception,
 *   it should re-throw, or the test failure will be muted.
 * - [ExecutionWrappingAction] may throw an exception on its own initiative, which will be considered a test failure.
 *
 * Requirements for [TestElement]s of types other than [Test]:
 * - If `elementAction` throws, it is considered a failure of the test framework.
 * - [ExecutionWrappingAction] should not throw to indicate a failing test or to block further tests from
 *   executing.
 */
typealias ExecutionWrappingAction = suspend TestElement.(elementAction: suspend TestElement.() -> Unit) -> Unit

/**
 * Returns a test configuration chaining [this] with a configuration disabling the [TestElement].
 *
 * Child elements inherit this setting's effect.
 */
fun TestConfig.disable() = parameterizing { isEnabled = false }

/**
 * Returns a test configuration chaining [this] with a coroutine [context].
 *
 * Child elements inherit the [context].
 */
fun TestConfig.coroutineContext(context: CoroutineContext): TestConfig = executionWrapping { elementAction ->
    withContext(context) {
        elementAction()
    }
}

/**
 * Returns a test configuration chaining [this] with an [executionWrappingAction] for a single test element.
 *
 * [executionWrappingAction] wraps around the [TestElement]'s cumulative action (a cumulative action includes
 * all wrapping actions following this one, and the elements primary action).
 * See [ExecutionWrappingAction] for requirements.
 *
 * The [executionWrappingAction] is performed at the level of its [TestElement] only.
 *
 * Example:
 * ```
 * configuration = TestConfig.aroundAll { elementAction ->
 *     withTimeout(2.seconds) {
 *         elementAction()
 *     }
 * }
 * ```
 */
fun TestConfig.aroundAll(executionWrappingAction: ExecutionWrappingAction): TestConfig =
    executionWrapping(executionWrappingAction)

/**
 * Returns a test configuration chaining [this] with an [executionWrappingAction] for elements of a [TestElement] tree.
 *
 * [executionWrappingAction] operates on the [TestElement] it is configured for and each of its child elements.
 * [executionWrappingAction] wraps around each [TestElement]'s cumulative action (a cumulative action includes
 * all wrapping actions following this one, and the elements primary action).
 * See [ExecutionWrappingAction] for requirements.
 *
 * Multiple [aroundEach] invocations nest outside-in in the order of appearance.
 *
 * Example:
 * ```
 * configuration = TestConfig.aroundEach { elementAction ->
 *     println("$elementPath aroundEach entering")
 *     elementAction()
 *     println("$elementPath aroundEach exiting")
 * }
 * ```
 */
fun TestConfig.aroundEach(executionWrappingAction: ExecutionWrappingAction): TestConfig =
    traversal(AroundEachTraversal(executionWrappingAction))

private class AroundEachTraversal(val executionWrappingAction: ExecutionWrappingAction) : TestExecutionTraversal {
    override suspend fun aroundEach(testElement: TestElement, elementAction: suspend TestElement.() -> Unit) {
        testElement.executionWrappingAction {
            testElement.elementAction()
        }
    }
}

/**
 * Returns a test configuration chaining [this] with a "fail fast" strategy to limit the number of test failures.
 *
 * If more than [maxFailureCount] tests fail, any subsequent test will abandon further testing:
 * - On platforms supporting it, the test session stops, optionally marking all remaining tests as skipped.
 * - On platforms not supporting a premature shutdown, all remaining tests will fail with a "Failing fast" exception,
 *   without being executed.
 *
 * The strategy covers the test element tree rooted at the configuration's element.
 */
fun TestConfig.failFast(maxFailureCount: Int) = traversal(FailFastStrategy(maxFailureCount))

private class FailFastStrategy(val maxFailureCount: Int) : TestExecutionTraversal {
    private val testFailureCount = atomic(0)

    override suspend fun aroundEach(testElement: TestElement, elementAction: suspend TestElement.() -> Unit) {
        if (testFailureCount.value > maxFailureCount && testElement is Test) {
            throw FailFastException(testFailureCount.value)
        }
        try {
            testElement.elementAction()
        } catch (throwable: Throwable) {
            if (testElement is Test) {
                testFailureCount.incrementAndGet()
            }
            throw throwable
        }
    }
}

internal class FailFastException(val failureCount: Int) : Error("Failing fast after $failureCount failed tests")

/**
 * Returns a test configuration chaining [this] with an [TestExecutionTraversal] for a test element tree.
 *
 * The traversal covers the [TestElement] it is configured for and all of its child elements.
 * Multiple traversals nest outside-in in the order of appearance.
 */
fun TestConfig.traversal(executionTraversal: TestExecutionTraversal): TestConfig = executionWrapping { elementAction ->
    val testElement = this
    withContext(ExecutionTraversalContext(executionTraversal)) {
        // The context element enables the traversal for this element's children only. To cover this element as well,
        // we explicitly invoke its traversal action.
        executionTraversal.aroundEach(testElement, elementAction)
    }
}

/**
 * A traversal following the execution across all [TestElement]s of a (partial) test element tree.
 *
 * For an example, see the implementation of [TestConfig.failFast].
 */
interface TestExecutionTraversal {
    /**
     * A method wrapping each [TestElement]'s cumulative [elementAction].
     *
     * The cumulative [elementAction] includes all wrapping actions following this one, and the elements primary action.
     */
    suspend fun aroundEach(testElement: TestElement, elementAction: suspend TestElement.() -> Unit)
}

private class ExecutionTraversalContext private constructor(
    /** [TestExecutionTraversal]s in an inside-out order of wrapping (innermost action first). */
    private val executionTraversals: List<TestExecutionTraversal>
) : AbstractCoroutineContextElement(Key) {

    /** Wraps the execution for this context's traversals, then executes [elementAction] on [testElement]. */
    suspend inline fun executeInside(testElement: TestElement, noinline elementAction: suspend TestElement.() -> Unit) {
        executionTraversals.fold(elementAction) { action, traversal ->
            { traversal.aroundEach(testElement, action) }
        }.invoke(testElement)
    }

    companion object {
        private val Key = object : CoroutineContext.Key<ExecutionTraversalContext> {}

        /** Returns a new [ExecutionTraversalContext], adding [executionTraversal] to the current ones. */
        suspend operator fun invoke(executionTraversal: TestExecutionTraversal): ExecutionTraversalContext {
            val current = current()
            return ExecutionTraversalContext(
                if (current != null) {
                    listOf(executionTraversal) + current.executionTraversals
                } else {
                    listOf(executionTraversal)
                }
            )
        }

        suspend fun current(): ExecutionTraversalContext? = currentCoroutineContext()[Key]
    }
}

private suspend inline fun <SpecificTestElement : TestElement> ExecutionWrappingAction?.wrapIfNotNull(
    testElement: SpecificTestElement,
    crossinline elementAction: suspend SpecificTestElement.() -> Unit
) {
    if (this != null) {
        this(testElement) {
            testElement.elementAction()
        }
    } else {
        testElement.elementAction()
    }
}

/**
 * Returns a test configuration chaining [this] with an invocation [value].
 *
 * Child elements inherit this setting.
 */
fun TestConfig.invocation(value: TestInvocation): TestConfig = coroutineContext(InvocationContext(value))

/**
 * The mode in which to invoke test elements.
 */
enum class TestInvocation {
    /** Execute test elements sequentially. */
    SEQUENTIAL,

    /** Execute test elements concurrently. */
    CONCURRENT;

    companion object {
        suspend fun current(): TestInvocation = currentCoroutineContext()[InvocationContext.Key]?.value ?: SEQUENTIAL
    }
}

private class InvocationContext(val value: TestInvocation) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<InvocationContext>
}

/**
 * Returns a test configuration chaining [this] with execution on a single-threaded dispatcher.
 *
 * Child elements inherit the single-threaded dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestConfig.singleThreaded(): TestConfig = executionWrapping { elementAction ->
    withSingleThreadedDispatcher { dispatcher ->
        withContext(dispatcher) {
            elementAction()
        }
    }
}

/**
 * Returns a test configuration chaining [this] with a main dispatcher (see [Dispatchers.setMain]).
 *
 * If [dispatcher] is `null`, a single-threaded dispatcher is used.
 *
 * This configuration may not be overridden at lower levels of the [TestElement] hierarchy.
 * Child elements inherit this setting.
 */
fun TestConfig.mainDispatcher(dispatcher: CoroutineDispatcher? = null): TestConfig =
    executionWrapping { elementAction ->
        withMainDispatcher(dispatcher) {
            elementAction()
        }
    }

/**
 * Returns a test configuration chaining [this] with a [kotlinx.coroutines.test.TestScope] setting.
 *
 * If [isEnabled] is true, tests will run in a [kotlinx.coroutines.test.TestScope] with the given [timeout].
 * Setting [isEnabled] to false will disable a previously enabled `TestScope` setting.
 *
 * Child elements inherit this setting.
 */
fun TestConfig.testScope(isEnabled: Boolean, timeout: Duration = 60.seconds): TestConfig =
    coroutineContext(TestScopeContext(isEnabled, timeout))

/**
 * A context element, which, when present and enabled, makes a test execute in [kotlinx.coroutines.test.TestScope].
 */
internal class TestScopeContext(internal val isEnabled: Boolean, val timeout: Duration) :
    AbstractCoroutineContextElement(Key) {
    companion object {
        private val Key = object : CoroutineContext.Key<TestScopeContext> {}

        suspend fun current(): TestScopeContext? = currentCoroutineContext()[Key]?.run { if (isEnabled) this else null }
    }
}

/**
 * Runs [action] with [dispatcher] set up as the main dispatcher, which will be reset afterward.
 *
 * If [dispatcher] is `null`, a single-threaded dispatcher is used.
 *
 * See [Dispatchers.setMain] for details. This function, if used exclusively, ensures that only one main dispatcher
 * is active at any point in time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun withMainDispatcher(dispatcher: CoroutineDispatcher? = null, action: suspend () -> Unit) {
    val previouslyChanged = mainDispatcherChanged.getAndSet(true)
    require(!previouslyChanged) {
        "Another invocation of withMainDispatcher() is still active." +
            " Redirecting Dispatchers.Main again would introduce a conflict in its global state.\n" +
            "\tPlease avoid concurrent changes to Dispatchers.Main by executing tests" +
            " in isolation (e.g. in a separate UI test compartment)."
    }

    suspend fun withDispatcherOrSingleThreaded(action: suspend (mainDispatcher: CoroutineDispatcher) -> Unit) {
        if (dispatcher != null) {
            action(dispatcher)
        } else {
            withSingleThreadedDispatcher {
                action(it)
            }
        }
    }

    withDispatcherOrSingleThreaded { mainDispatcher ->
        Dispatchers.setMain(mainDispatcher)
        try {
            action()
        } finally {
            Dispatchers.resetMain()
            check(mainDispatcherChanged.getAndSet(false) == true)
        }
    }
}

private val mainDispatcherChanged = atomic(false)

/**
 * Returns a test configuration chaining [this] with a [TestReport] for a test element tree.
 *
 * The report covers the [TestElement] it is configured for and all of its child elements.
 */
fun TestConfig.report(report: TestReport): TestConfig = reportSetup { elementAction ->
    val testElement = this
    withContext(ReportContext(report)) {
        elementAction(testElement)
    }
}

private class ReportContext private constructor(
    /** [TestReport]s in definition order. */
    val additionalReports: List<TestReport>
) : AbstractCoroutineContextElement(Key) {

    companion object {
        private val Key = object : CoroutineContext.Key<ReportContext> {}

        /** Returns a new [ReportContext], adding [additionalReport] to the current ones. */
        suspend operator fun invoke(additionalReport: TestReport): ReportContext {
            val additionalReports = additionalReports()
            return ReportContext(
                if (additionalReports != null) {
                    additionalReports + listOf(additionalReport)
                } else {
                    listOf(additionalReport)
                }
            )
        }

        suspend fun additionalReports(): Iterable<TestReport>? = currentCoroutineContext()[Key]?.additionalReports
    }
}
