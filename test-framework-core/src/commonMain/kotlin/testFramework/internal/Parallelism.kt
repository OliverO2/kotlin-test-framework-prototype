package testFramework.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal suspend fun <Result> withParallelism(parallelism: Int?, action: suspend () -> Result): Result =
    if (parallelism == null) {
        action()
    } else {
        withContext(dispatcherWithParallelism(parallelism)) {
            action()
        }
    }

internal expect fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher

expect suspend fun withSingleThreading(action: suspend () -> Unit)
