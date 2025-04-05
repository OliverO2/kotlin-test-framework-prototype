package testFramework

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The platform executing tests.
 */
interface TestPlatform {
    enum class Type { JVM, NATIVE, JS, WASM_JS, WASM_WASI }

    /** The platform's type. */
    val type: Type

    /** The platform's human-readable name. NOTE: Consider it unstable, use [type] for conditional code. */
    val displayName: String

    /** The platform's default parallelism. */
    val parallelism: Int

    /** The ID of the current thread. NOTE: For debugging purposes only, do not make assumptions about its value. */
    fun threadId(): ULong

    /** The display name of the current thread. NOTE: For debugging purposes only, do not make assumptions about it. */
    fun threadDisplayName(): String
}

/**
 * The [TestPlatform] currently used to execute tests.
 */
expect val testPlatform: TestPlatform

/**
 * Returns a [CoroutineDispatcher] providing a maximum amount of [parallelism], limited by the platform's capabilities.
 *
 * Use this utility function for testing special cases.
 */
expect fun dispatcherWithParallelism(parallelism: Int): CoroutineDispatcher

/**
 * Returns a [CoroutineDispatcher] which executes all of its coroutines on a single thread.
 *
 * Use this utility function for testing special cases.
 */
expect suspend fun withSingleThreading(action: suspend () -> Unit)
