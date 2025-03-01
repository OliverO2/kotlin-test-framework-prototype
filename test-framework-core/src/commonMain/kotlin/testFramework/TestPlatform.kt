package testFramework

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The platform executing tests.
 */
interface TestPlatform {
    /**
     * The platform's human-readable name. NOTE: Consider it unstable, do not depend on this name in code.
     *
     * If you need code to depend on a specific platform, use a platform object comparison like this:
     * ```
     * if (testPlatform === TestPlatformWasmWasi) ...
     * ```
     */
    val displayName: String

    /** The platform's default parallelism. */
    val parallelism: Int

    /** The ID of the current thread. NOTE: For debugging purposes only, do not make assumptions about its value. */
    fun threadId(): ULong

    /** The display name of the current thread. NOTE: For debugging purposes only, do not make assumptions about it. */
    fun threadDisplayName(): String
}

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
