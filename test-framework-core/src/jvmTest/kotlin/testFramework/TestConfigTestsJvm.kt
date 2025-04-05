package testFramework

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import testFramework.internal.TestFramework
import testFramework.internal.initializeTestFramework
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TestConfigTestsJvm {
    @Test
    fun mainDispatcherRunBlocking() = runBlocking {
        testMainDispatcher()
    }

    @Test
    fun mainDispatcherRunTest() = runTest {
        testMainDispatcher()
    }

    @OptIn(ExperimentalStdlibApi::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private suspend fun testMainDispatcher() {
        initializeTestFramework()
        try {
            val testSuite by testSuite("testSuite") {}

            val originalMainThreadId = withContext(Dispatchers.Main) { testPlatform.threadId() }
            val alternativeMainDispatcher = newSingleThreadContext("UI thread")
            val alternativeMainThreadId = withContext(alternativeMainDispatcher) { testPlatform.threadId() }

            // Sanity check
            assertNotEquals(originalMainThreadId, alternativeMainThreadId)

            // Default: on original main dispatcher
            TestConfig.executeWrapped(testSuite) {
                withContext(Dispatchers.Main) {
                    assertEquals(originalMainThreadId, testPlatform.threadId())
                }
            }

            // Context setting: on alternative main dispatcher
            TestConfig.mainDispatcher(alternativeMainDispatcher).executeWrapped(testSuite) {
                withContext(Dispatchers.Main) {
                    assertEquals(alternativeMainThreadId, testPlatform.threadId())
                }
            }

            // Finally: back to original main dispatcher
            withContext(Dispatchers.Main) {
                assertEquals(originalMainThreadId, testPlatform.threadId())
            }
        } finally {
            // Reset global state for another round of test framework initialization.
            TestFramework.resetState()
        }
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun mainDispatcherSetMoreThanOnce() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        val alternativeMainDispatcher = newSingleThreadContext("UI thread")

        TestConfig.mainDispatcher(alternativeMainDispatcher).executeWrapped(testSuite) {
            assertFailsWith<IllegalArgumentException> {
                TestConfig.mainDispatcher(Dispatchers.Unconfined).executeWrapped(testSuite) {
                    // Must fail: setting a main dispatcher again
                }
            }.assertMessageStartsWith("Another invocation of withMainDispatcher() is still active.")
        }
    }
}
