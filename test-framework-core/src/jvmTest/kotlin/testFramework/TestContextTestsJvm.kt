package testFramework

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TestContextTestsJvm {
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
        val originalMainThreadId = withContext(Dispatchers.Main) { testPlatform.threadId() }
        val alternativeMainDispatcher = newSingleThreadContext("UI thread")
        val alternativeMainThreadId = withContext(alternativeMainDispatcher) { testPlatform.threadId() }

        // Sanity check
        assertNotEquals(originalMainThreadId, alternativeMainThreadId)

        // Default: on original main dispatcher
        TestContext.executeWithin {
            withContext(Dispatchers.Main) {
                assertEquals(originalMainThreadId, testPlatform.threadId())
            }
        }

        // Context setting: on alternative main dispatcher
        TestContext.mainDispatcher(alternativeMainDispatcher).executeWithin {
            withContext(Dispatchers.Main) {
                assertEquals(alternativeMainThreadId, testPlatform.threadId())
            }
        }

        // Finally: back to original main dispatcher
        withContext(Dispatchers.Main) {
            assertEquals(originalMainThreadId, testPlatform.threadId())
        }
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun mainDispatcherSetMoreThanOnce() = runTest {
        val alternativeMainDispatcher = newSingleThreadContext("UI thread")
        TestContext.mainDispatcher(alternativeMainDispatcher).executeWithin {
            assertFailsWith<IllegalArgumentException> {
                TestContext.mainDispatcher(Dispatchers.Unconfined).executeWithin {
                    // Must fail: setting a main dispatcher again
                }
            }.assertMessageStartsWith("Another invocation of withMainDispatcher() is still active.")
        }
    }
}
