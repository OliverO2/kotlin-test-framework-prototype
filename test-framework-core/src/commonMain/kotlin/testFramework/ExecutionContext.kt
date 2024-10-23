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

interface ExecutionContext {
    val wrappingAction: ExecutionWrappingAction

    class Invocation(private val mode: Mode) : ExecutionContext {
        enum class Mode {
            SEQUENTIAL,
            CONCURRENT
        }

        override val wrappingAction: ExecutionWrappingAction = { innerAction ->
            withContext(Context(mode)) {
                innerAction()
            }
        }

        private class Context(val mode: Mode) : AbstractCoroutineContextElement(Key) {
            companion object Key : CoroutineContext.Key<Context>
        }

        companion object {
            suspend fun mode(): Mode = currentCoroutineContext()[Context.Key]?.mode ?: Mode.SEQUENTIAL
        }
    }

    class CoroutineContext(private val context: kotlin.coroutines.CoroutineContext) : ExecutionContext {
        override val wrappingAction: ExecutionWrappingAction = { innerAction ->
            withContext(context) {
                innerAction()
            }
        }
    }

    class MainDispatcher(private val dispatcher: CoroutineDispatcher) : ExecutionContext {
        override val wrappingAction: ExecutionWrappingAction = { innerAction ->
            withMainDispatcher(dispatcher) {
                innerAction()
            }
        }
    }

    class TestScope(private val isEnabled: Boolean, val timeout: Duration = DEFAULT_TIMEOUT) : ExecutionContext {
        override val wrappingAction: ExecutionWrappingAction = { innerAction ->
            withContext(Context(this@TestScope.isEnabled, timeout)) {
                innerAction()
            }
        }

        class Context(internal val isEnabled: Boolean, val timeout: Duration) : AbstractCoroutineContextElement(Key) {
            internal companion object Key : CoroutineContext.Key<Context>
        }

        companion object {
            val DEFAULT_TIMEOUT = 60.seconds

            suspend fun contextOrNull(): Context? =
                currentCoroutineContext()[Context.Key]?.run { if (isEnabled) this else null }
        }
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
