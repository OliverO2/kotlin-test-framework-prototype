package testFramework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

actual val testPlatform: TestPlatform = TestPlatformJvm

object TestPlatformJvm : TestPlatform {
    override val type: TestPlatform.Type = TestPlatform.Type.JVM
    override val displayName = "JVM"
    override val parallelism = Runtime.getRuntime().availableProcessors()
    override fun threadId() = Thread.currentThread().id.toULong()
    override fun threadDisplayName() = Thread.currentThread().name ?: "(thread ${threadId()})"
}

actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(parallelism)

actual suspend fun withSingleThreadedDispatcher(action: suspend (dispatcher: CoroutineDispatcher) -> Unit) {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    newSingleThreadContext("single-threading").use { dispatcher ->
        action(dispatcher)
    }
}
