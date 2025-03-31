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
 * Each [TestConfig] configures its element in two phases:
 * 1. The parameterization phase, which is executed when creating the test tree.
 * 2. The execution phase, where the elements of the test tree are executed.
 *
 * When [TestConfig]s are combined into a chain, a later [TestConfig] takes precedence over an earlier one.
 *
 * Use an existing configuration or the empty [TestConfig] object as the starting point, then invoke the [TestConfig]'s
 * methods to return modified configurations. Configurations can be chained, with the last configuration gaining
 * precedence over previous configurations of the same kind.
 *
 * Example:
 * ```
 * configuration = TestConfig
 *     .coroutineContext(UnconfinedTestDispatcher())
 *     .invocation(InvocationContext.Mode.CONCURRENT)
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

    /** Returns a [TestConfig] which combines `this` configuration with [innerExecutionWrappingAction]. */
    internal infix fun combinedWith(innerExecutionWrappingAction: ExecutionWrappingAction): TestConfig =
        combinedWith(null, innerExecutionWrappingAction)

    /** Returns a [TestConfig] which combines `this` configuration with [otherConfiguration]. */
    internal infix fun combinedWith(otherConfiguration: TestConfig): TestConfig =
        combinedWith(otherConfiguration.parameterizingAction, otherConfiguration.executionWrappingAction)

    internal open fun parameterize(testElement: TestElement) {
        if (parameterizingAction != null) testElement.parameterizingAction()
    }

    internal suspend fun executeWithin(innerAction: suspend () -> Unit) {
        if (executionWrappingAction != null) {
            executionWrappingAction {
                innerAction()
            }
        } else {
            innerAction()
        }
    }

    /** The initial (empty) test configuration. */
    companion object : TestConfig(null, null)
}

private typealias ParameterizingAction = TestElement.() -> Unit
private typealias ExecutionWrappingAction = suspend (innerAction: suspend () -> Unit) -> Unit

/** Returns a test configuration combining [this] with a configuration disabling the test element. */
fun TestConfig.disable() = combinedWith({ isEnabled = false }, {})

/** Returns a test configuration combining [this] with a coroutine [context]. */
fun TestConfig.coroutineContext(context: CoroutineContext): TestConfig = combinedWith { innerAction ->
    withContext(context) {
        innerAction()
    }
}

/** Returns a test configuration combining [this] with an invocation [mode]. */
fun TestConfig.invocation(mode: InvocationContext.Mode): TestConfig = coroutineContext(InvocationContext(mode))

class InvocationContext internal constructor(val mode: Mode) : AbstractCoroutineContextElement(Key) {
    enum class Mode { SEQUENTIAL, CONCURRENT }

    companion object {
        private val Key = object : CoroutineContext.Key<InvocationContext> {}

        suspend fun mode(): Mode = currentCoroutineContext()[Key]?.mode ?: Mode.SEQUENTIAL
    }
}

/**
 * Returns a test configuration combining [this] with a main dispatcher (see [Dispatchers.setMain]).
 *
 * This configuration may not be overridden at lower levels of the [TestElement] hierarchy.
 */
fun TestConfig.mainDispatcher(dispatcher: CoroutineDispatcher): TestConfig = combinedWith { innerAction ->
    withMainDispatcher(dispatcher) {
        innerAction()
    }
}

/**
 * Returns a test configuration combining [this] with a [kotlinx.coroutines.test.TestScope] setting.
 *
 * If [isEnabled] is true, tests will run in a [kotlinx.coroutines.test.TestScope] with the given [timeout].
 * Setting [isEnabled] to false will disable a previously enabled `TestScope` setting.
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
