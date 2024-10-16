package testFramework

import kotlinx.coroutines.CoroutineDispatcher

expect fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher

expect suspend fun withSingleThreading(action: suspend () -> Unit)
