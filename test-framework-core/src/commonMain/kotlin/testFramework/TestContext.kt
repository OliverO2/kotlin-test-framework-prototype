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
 * A test element execution context, containing configuration parts which modify the execution of tests and suites.
 *
 * The configuration parts of a [TestContext] apply to all [Test]s in a hierarchy unless some or all
 * parts are overridden via another [TestContext].
 *
 * Use an existing context or the empty [TestContext] object as the starting point, then invoke the [TestContext]'s
 * methods to return modified contexts. Modifications can be chained, with the last modification gaining precedence
 * over previous modifications of the same kind.
 *
 * Example:
 * ```
 * context = TestContext.coroutineContext(UnconfinedTestDispatcher()).invocation(InvocationContext.Mode.CONCURRENT)
 * ```
 */
open class TestContext private constructor(private val wrappingAction: ExecutionWrappingAction) {
    /** Returns a [TestContext] which wraps `this` context's [wrappingAction] around [innerWrapAround]. */
    internal open infix fun wrapping(innerWrapAround: ExecutionWrappingAction): TestContext =
        TestContext { innerAction ->
            wrappingAction {
                innerWrapAround(innerAction)
            }
        }

    /** Returns a [TestContext] which wraps `this` context's [wrappingAction] around that of [innerContext]. */
    internal infix fun wrapping(innerContext: TestContext): TestContext = wrapping(innerContext.wrappingAction)

    internal open suspend fun executeWithin(innerAction: suspend () -> Unit) {
        wrappingAction {
            innerAction()
        }
    }

    /**
     * The initial (empty) test context without any configuration.
     */
    companion object : TestContext({}) {
        override infix fun wrapping(innerWrapAround: ExecutionWrappingAction): TestContext =
            TestContext(innerWrapAround)

        override suspend fun executeWithin(innerAction: suspend () -> Unit) = innerAction()
    }
}

private typealias ExecutionWrappingAction = suspend (innerAction: suspend () -> Unit) -> Unit

/** Returns a test context combining [this] with an invocation [mode]. */
fun TestContext.invocation(mode: InvocationContext.Mode): TestContext = wrapping { innerAction ->
    withContext(InvocationContext(mode)) {
        innerAction()
    }
}

class InvocationContext internal constructor(val mode: Mode) : AbstractCoroutineContextElement(Key) {
    enum class Mode { SEQUENTIAL, CONCURRENT }

    companion object {
        private val Key = object : CoroutineContext.Key<InvocationContext> {}

        suspend fun mode(): Mode = currentCoroutineContext()[Key]?.mode ?: Mode.SEQUENTIAL
    }
}

/** Returns a test context combining [this] with a coroutine [context]. */
fun TestContext.coroutineContext(context: CoroutineContext): TestContext = wrapping { innerAction ->
    withContext(context) {
        innerAction()
    }
}

/**
 * Returns a test context combining [this] with a main dispatcher (see [Dispatchers.setMain]).
 *
 * This configuration may not be overridden at lower levels of the [TestElement] hierarchy.
 */
fun TestContext.mainDispatcher(dispatcher: CoroutineDispatcher): TestContext = wrapping { innerAction ->
    withMainDispatcher(dispatcher) {
        innerAction()
    }
}

/**
 * Returns a test context combining [this] with a [kotlinx.coroutines.test.TestScope] setting.
 *
 * If [isEnabled] is true, tests will run in a [kotlinx.coroutines.test.TestScope] with the given [timeout].
 * Setting [isEnabled] to false will disable a previously enabled `TestScope` setting.
 */
fun TestContext.testScope(isEnabled: Boolean, timeout: Duration = TestScopeContext.DEFAULT_TIMEOUT): TestContext =
    wrapping { innerAction ->
        withContext(TestScopeContext(isEnabled, timeout)) {
            innerAction()
        }
    }

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
