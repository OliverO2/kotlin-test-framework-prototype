package de.infix.testBalloon.integration.blockingDetection

import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.coroutineContext
import de.infix.testBalloon.framework.testScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import reactor.blockhound.BlockHound
import reactor.blockhound.BlockingOperationError
import reactor.blockhound.integration.BlockHoundIntegration
import kotlin.coroutines.CoroutineContext

actual fun TestConfig.blockingDetection(mode: BlockingDetection): TestConfig = if (mode == BlockingDetection.DISABLED) {
    coroutineContext(BlockHoundContextElement(mode))
} else {
    testScope(isEnabled = false).coroutineContext(BlockHoundContextElement(mode))
}

/**
 * Execute [action] in a coroutine scope governed by the specified blockingDetection [mode].
 *
 * Example:
 * ```
 *     withBlockHoundMode(BlockingDetection.DISABLED) { someBlockingCall() }
 * ```
 */
suspend fun <R> withBlockingDetection(mode: BlockingDetection, action: suspend () -> R): R =
    withContext(BlockHoundContextElement(mode)) {
        action()
    }

private class BlockHoundContextElement(private val mode: BlockingDetection) : ThreadContextElement<BlockingDetection> {
    init {
        if (mode != BlockingDetection.DISABLED) {
            initialize()
        }
    }

    override val key: CoroutineContext.Key<BlockHoundContextElement> = Key

    override fun updateThreadContext(context: CoroutineContext): BlockingDetection {
        // invoked before the coroutine is resumed on the current thread
        val oldState = threadLocalMode.get()
        threadLocalMode.set(mode)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: BlockingDetection) {
        // invoked after the coroutine has suspended on the current thread
        threadLocalMode.set(oldState)
    }

    companion object {
        private val Key = object : CoroutineContext.Key<BlockHoundContextElement> {}

        val effectiveMode: BlockingDetection get() = threadLocalMode.get()

        private var isInitialized = false
        private var threadLocalMode = ThreadLocal.withInitial { BlockingDetection.DISABLED }

        private fun initialize() {
            if (!isInitialized) {
                BlockHound.install()
                isInitialized = true
            }
        }
    }
}

class TestBalloonBlockHoundIntegration : BlockHoundIntegration {
    override fun applyTo(builder: BlockHound.Builder): Unit = with(builder) {
        allowBlockingCallsInside("java.util.ServiceLoader${'$'}LazyClassPathLookupIterator", "parse")

        blockingMethodCallback {
            when (BlockHoundContextElement.effectiveMode) {
                BlockingDetection.ERROR -> throw BlockingOperationError(it)
                BlockingDetection.PRINT -> BlockingOperationError(it).printStackTrace()
                BlockingDetection.DISABLED -> {}
            }
        }
    }
}
