package testFramework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import testFramework.internal.runTestAwaitingCompletion
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface Concurrency {
    suspend fun runInContext(action: suspend (testScope: TestScope?) -> Unit)

    interface SuiteConcurrency : Concurrency

    interface TestConcurrency : Concurrency

    object Sequential : SuiteConcurrency {
        override suspend fun runInContext(action: suspend (testScope: TestScope?) -> Unit) = action(null)
    }

    class Parallel(val dispatcher: CoroutineDispatcher = Dispatchers.Default) :
        SuiteConcurrency,
        TestConcurrency {

        override suspend fun runInContext(action: suspend (testScope: TestScope?) -> Unit) = withContext(dispatcher) {
            action(null)
        }
    }

    class TestScoped(val context: CoroutineContext = EmptyCoroutineContext, val timeout: Duration = DEFAULT_TIMEOUT) :
        TestConcurrency {

        override suspend fun runInContext(action: suspend (testScope: TestScope?) -> Unit) {
            var inheritableContext = currentCoroutineContext().minusKey(Job)
            if (inheritableContext[CoroutineDispatcher] !is TestDispatcher) {
                inheritableContext = inheritableContext.minusKey(CoroutineDispatcher)
            }
            TestScope(inheritableContext + context)
                .runTestAwaitingCompletion(timeout = timeout) {
                    action(this)
                }
        }

        companion object {
            val DEFAULT_TIMEOUT = 60.seconds
        }
    }

    object Inherited : SuiteConcurrency, TestConcurrency {
        override suspend fun runInContext(action: suspend (testScope: TestScope?) -> Unit) = action(null)
    }
}
