package testFramework.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher = Dispatchers.Default

internal actual val platformParallelism: Int = 1

internal actual suspend fun withSingleThreading(action: suspend () -> Unit) = action()
