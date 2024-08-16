package testFramework.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(parallelism)

internal actual val platformParallelism: Int = Runtime.getRuntime().availableProcessors()
