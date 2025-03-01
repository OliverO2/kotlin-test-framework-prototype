package testFramework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher =
    Dispatchers.Default // single-threaded on Wasm/WASI until shared-everything threads are available

actual suspend fun withSingleThreading(action: suspend () -> Unit) = action()
