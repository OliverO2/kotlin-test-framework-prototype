package testFramework

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
 * A test element may be configured via any number of chained [TestConfig]s.
 *
 * Each [TestConfig] configures its [TestElement] in two phases:
 * 1. The parameterization phase, which is executed when creating the test tree.
 * 2. The execution phase, where the elements of the test tree are executed.
 *
 * When [TestConfig]s are combined into a chain,
 * - a later conflicting [TestConfig] takes precedence over an earlier one,
 * - non-conflicting coroutine context elements accumulate,
 * - a series of [aroundAll] wrappers nest from the outside to the inside in order of appearance.
 *
 * Each [TestConfig] operates at the [TestElement] level where it is configured. However, child elements inherit
 * some configuration effects as described in the respective [TestConfig] function.
 *
 * Use an existing configuration or the empty [TestConfig] object as the starting point, then invoke the [TestConfig]
 * functions to return modified configurations.
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
    private val executionWrappingAction: ExecutionWrappingAction?
) {
    /** Returns a [TestConfig] which combines `this` configuration with the parameters provided. */
    internal fun combinedWith(
        nextParameterizingAction: ParameterizingAction? = null,
        innerExecutionWrappingAction: ExecutionWrappingAction? = null
    ): TestConfig = TestConfig(
        parameterizingAction = if (parameterizingAction != null && nextParameterizingAction != null) {
            {
                parameterizingAction()
                nextParameterizingAction()
            }
        } else {
            parameterizingAction ?: nextParameterizingAction
        },
        executionWrappingAction = if (executionWrappingAction != null && innerExecutionWrappingAction != null) {
            { innerAction ->
                executionWrappingAction {
                    innerExecutionWrappingAction(innerAction)
                }
            }
        } else {
            executionWrappingAction ?: innerExecutionWrappingAction
        }
    )

    /** Returns a [TestConfig] which chains `this` configuration with [otherConfiguration]. */
    fun chainedWith(otherConfiguration: TestConfig): TestConfig =
        combinedWith(otherConfiguration.parameterizingAction, otherConfiguration.executionWrappingAction)

    /** Parameterizes [testElement] according to `this` configuration. */
    internal open fun parameterize(testElement: TestElement) {
        if (parameterizingAction != null) testElement.parameterizingAction()
    }

    /** Wraps the execution according to `this` configuration, then executes [innerAction] on [testElement]. */
    internal suspend fun <SpecificTestElement : TestElement> executeWrapped(
        testElement: SpecificTestElement,
        innerAction: suspend SpecificTestElement.() -> Unit
    ) {
        if (executionWrappingAction != null) {
            testElement.executionWrappingAction {
                testElement.innerAction()
            }
        } else {
            testElement.innerAction()
        }
    }

    /** The initial (empty) test configuration. */
    companion object : TestConfig(null, null)
}

private typealias ParameterizingAction = TestElement.() -> Unit
typealias ExecutionWrappingAction = suspend TestElement.(innerAction: suspend TestElement.() -> Unit) -> Unit

/**
 * Returns a test configuration chaining [this] with a configuration disabling the [TestElement].
 *
 * Child elements inherit this setting.
 */
fun TestConfig.disable() = combinedWith({ isEnabled = false })

/**
 * Returns a test configuration chaining [this] with a coroutine [context].
 *
 * Child elements inherit the [context].
 */
fun TestConfig.coroutineContext(context: CoroutineContext): TestConfig = combinedWith { innerAction ->
    withContext(context) {
        innerAction()
    }
}

/**
 * Returns a test configuration chaining [this] with an [executionWrappingAction].
 *
 * [executionWrappingAction] wraps a (suspending) inner action around all following configurations and, finally,
 * around the execution action of the [TestElement] where the configuration has been applied.
 *
 * Example:
 * ```
 * configuration = TestConfig.aroundAll { innerAction ->
 *     withTimeout(2.seconds) {
 *         innerAction()
 *     }
 * }
 * ```
 *
 * The [executionWrappingAction] is performed at the level of its [TestElement] only, although child elements inherit
 * possible changes to the coroutine context.
 */
fun TestConfig.aroundAll(executionWrappingAction: ExecutionWrappingAction): TestConfig =
    combinedWith(innerExecutionWrappingAction = executionWrappingAction)

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
 * Returns a test configuration chaining [this] with a main dispatcher (see [Dispatchers.setMain]).
 *
 * This configuration may not be overridden at lower levels of the [TestElement] hierarchy.
 * Child elements inherit this setting.
 */
fun TestConfig.mainDispatcher(dispatcher: CoroutineDispatcher): TestConfig = combinedWith { innerAction ->
    withMainDispatcher(dispatcher) {
        innerAction()
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
fun TestConfig.testScope(isEnabled: Boolean, timeout: Duration = TestScopeContext.DEFAULT_TIMEOUT): TestConfig =
    coroutineContext(TestScopeContext(isEnabled, timeout))

/**
 * A context element, which, when present and enabled, makes a test execute in [kotlinx.coroutines.test.TestScope].
 */
internal class TestScopeContext(internal val isEnabled: Boolean, val timeout: Duration) :
    AbstractCoroutineContextElement(Key) {
    companion object {
        private val Key = object : CoroutineContext.Key<TestScopeContext> {}

        val DEFAULT_TIMEOUT = 60.seconds

        suspend fun current(): TestScopeContext? = currentCoroutineContext()[Key]?.run { if (isEnabled) this else null }
    }
}

/**
 * Runs [action] with [dispatcher] set up as the main dispatcher, which will be reset afterward.
 *
 * See [Dispatchers.setMain] for details. This function, if used exclusively, ensures that only one main dispatcher
 * is active at any point in time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun withMainDispatcher(dispatcher: CoroutineDispatcher, action: suspend () -> Unit) {
    val previouslyChanged = mainDispatcherChanged.getAndSet(true)
    require(!previouslyChanged) {
        "Another invocation of withMainDispatcher() is still active." +
            " Redirecting Dispatchers.Main again would introduce a conflict in its global state.\n" +
            "\tPlease avoid concurrent changes to Dispatchers.Main by executing tests" +
            " in isolation (e.g. in a separate UI test compartment)."
    }
    Dispatchers.setMain(dispatcher)
    try {
        action()
    } finally {
        Dispatchers.resetMain()
        check(mainDispatcherChanged.getAndSet(false) == true)
    }
}

private val mainDispatcherChanged = atomic(false)
