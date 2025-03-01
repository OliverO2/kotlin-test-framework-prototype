package testFramework

import kotlinx.coroutines.CoroutineDispatcher

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
