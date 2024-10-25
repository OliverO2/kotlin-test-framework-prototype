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

typealias ExecutionWrappingAction = suspend (innerAction: suspend () -> Unit) -> Unit

open class TestContext(private val wrappingAction: ExecutionWrappingAction) {
    open infix fun wrapping(innerWrapAround: ExecutionWrappingAction): TestContext = TestContext { innerAction ->
        wrappingAction {
            innerWrapAround(innerAction)
        }
    }

    infix fun wrapping(innerContext: TestContext): TestContext = wrapping(innerContext.wrappingAction)

    open suspend fun executeWithin(innerAction: suspend () -> Unit) {
        wrappingAction {
            innerAction()
        }
    }

    companion object : TestContext({}) {
        override infix fun wrapping(innerWrapAround: ExecutionWrappingAction): TestContext =
            TestContext(innerWrapAround)

        override suspend fun executeWithin(innerAction: suspend () -> Unit) = innerAction()
    }
}

fun TestContext.invocation(mode: InvocationContext.Mode): TestContext = wrapping { innerAction ->
    withContext(InvocationContext(mode)) {
        innerAction()
    }
}

class InvocationContext(val mode: Mode) : AbstractCoroutineContextElement(Key) {
    enum class Mode { SEQUENTIAL, CONCURRENT }

    companion object {
        private val Key = object : CoroutineContext.Key<InvocationContext> {}

        suspend fun mode(): Mode = currentCoroutineContext()[Key]?.mode ?: Mode.SEQUENTIAL
    }
}

fun TestContext.coroutineContext(context: CoroutineContext): TestContext = wrapping { innerAction ->
    withContext(context) {
        innerAction()
    }
}

fun TestContext.mainDispatcher(dispatcher: CoroutineDispatcher): TestContext = wrapping { innerAction ->
    withMainDispatcher(dispatcher) {
        innerAction()
    }
}

fun TestContext.testScope(isEnabled: Boolean, timeout: Duration = TestScopeContext.DEFAULT_TIMEOUT): TestContext =
    wrapping { innerAction ->
        withContext(TestScopeContext(isEnabled, timeout)) {
            innerAction()
        }
    }

class TestScopeContext(internal val isEnabled: Boolean, val timeout: Duration) : AbstractCoroutineContextElement(Key) {
    companion object {
        private val Key = object : CoroutineContext.Key<TestScopeContext> {}

        val DEFAULT_TIMEOUT = 60.seconds

        suspend fun current(): TestScopeContext? = currentCoroutineContext()[Key]?.run { if (isEnabled) this else null }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun withMainDispatcher(dispatcher: CoroutineDispatcher, action: suspend () -> Unit) {
    val previouslyChanged = mainDispatcherChanged.getAndSet(true)
    require(!previouslyChanged) {
        "Another invocation of withMainDispatcher() is still active." +
            " Redirecting Dispatchers.Main again would introduce a conflict in its global state.\n" +
            "\tPlease avoid concurrent changes to Dispatchers.Main by running tests" +
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
