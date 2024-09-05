package testFramework.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import platform.posix._SC_NPROCESSORS_ONLN
import platform.posix.sysconf

@OptIn(ExperimentalCoroutinesApi::class)
actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(parallelism)

actual val platformParallelism: Int = sysconf(_SC_NPROCESSORS_ONLN).toInt()

actual suspend fun withSingleThreading(action: suspend () -> Unit) {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val dispatcher = newSingleThreadContext("single-threading")
    AutoCloseable { dispatcher.close() }.use {
        withContext(dispatcher) { action() }
    }
}
