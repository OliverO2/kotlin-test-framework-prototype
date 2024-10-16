package testFramework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher = Dispatchers.Default

actual suspend fun withSingleThreading(action: suspend () -> Unit) = action()
