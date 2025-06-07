package de.infix.testBalloon.framework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.newSingleThreadContext
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker

actual val testPlatform: TestPlatform = TestPlatformNative

object TestPlatformNative : TestPlatform {
    override val type: TestPlatform.Type = TestPlatform.Type.NATIVE

    @OptIn(ExperimentalNativeApi::class)
    override val displayName = "Native/${Platform.cpuArchitecture.name}/${Platform.osFamily.name}"

    @OptIn(ExperimentalNativeApi::class)
    override val parallelism = Platform.getAvailableProcessors()

    @OptIn(ExperimentalStdlibApi::class, ObsoleteWorkersApi::class)
    override fun threadId(): ULong = Worker.current.platformThreadId
    override fun threadDisplayName() = threadId().toString()
}

actual fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(parallelism)

actual suspend fun withSingleThreadedDispatcher(action: suspend (dispatcher: CoroutineDispatcher) -> Unit) {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val dispatcher = newSingleThreadContext("single-threading")
    AutoCloseable { dispatcher.close() }.use {
        action(dispatcher)
    }
}
