package testFramework

import kotlinx.coroutines.Dispatchers
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
    fun singleThreadedDispatcher() = withTestFramework {
        withSingleThreadedDispatcher { defaultDispatcher ->
            val testSuite by testSuite("testSuite", configuration = TestConfig.coroutineContext(defaultDispatcher)) {}

            val defaultDispatcherThreadId = testPlatform.threadId()

            TestConfig.executeWrapped(testSuite) {
                assertEquals(defaultDispatcherThreadId, testPlatform.threadId())
            }

            TestConfig.singleThreaded().executeWrapped(testSuite) {
                assertNotEquals(defaultDispatcherThreadId, testPlatform.threadId())
            }
        }
    }

    @Test
    fun mainDispatcherRunBlocking() = runBlocking {
        testMainDispatcher()
    }

    @Test
    fun mainDispatcherRunTest() = runTest {
        testMainDispatcher()
    }

    private suspend fun testMainDispatcher() {
        initializeTestFramework()
        try {
            val testSuite by testSuite("testSuite") {}

            // Using the platform's main dispatcher.
            val platformMainThreadId = withContext(Dispatchers.Main) { testPlatform.threadId() }

            withSingleThreadedDispatcher { alternativeMainDispatcher ->
                val alternativeMainThreadId = withContext(alternativeMainDispatcher) { testPlatform.threadId() }

                // Sanity check
                assertNotEquals(platformMainThreadId, alternativeMainThreadId)

                // Default: on original main dispatcher
                TestConfig.executeWrapped(testSuite) {
                    withContext(Dispatchers.Main) {
                        assertEquals(platformMainThreadId, testPlatform.threadId())
                    }
                }

                // Configuration: on explicit alternative main dispatcher
                TestConfig.mainDispatcher(alternativeMainDispatcher).executeWrapped(testSuite) {
                    withContext(Dispatchers.Main) {
                        assertEquals(alternativeMainThreadId, testPlatform.threadId())
                    }
                }

                // Configuration: on default alternative main dispatcher
                TestConfig.mainDispatcher().executeWrapped(testSuite) {
                    withContext(Dispatchers.Main) {
                        assertNotEquals(platformMainThreadId, testPlatform.threadId())
                        assertNotEquals(alternativeMainThreadId, testPlatform.threadId())
                    }
                }

                // Finally: back to original main dispatcher
                withContext(Dispatchers.Main) {
                    assertEquals(platformMainThreadId, testPlatform.threadId())
                }
            }
        } finally {
            // Reset global state for another round of test framework initialization.
            TestFramework.resetState()
        }
    }

    @Test
    fun mainDispatcherSetMoreThanOnce() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        withSingleThreadedDispatcher { alternativeMainDispatcher ->
            TestConfig.mainDispatcher(alternativeMainDispatcher).executeWrapped(testSuite) {
                assertFailsWith<IllegalArgumentException> {
                    TestConfig.mainDispatcher(Dispatchers.Unconfined).executeWrapped(testSuite) {
                        // Must fail: setting a main dispatcher again
                    }
                }.assertMessageStartsWith("Another invocation of withMainDispatcher() is still active.")
            }
        }
    }
}
