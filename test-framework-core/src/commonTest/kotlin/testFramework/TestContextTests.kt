package testFramework

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestContextTests {
    @Test
    fun invocation() = runTest {
        TestContext.executeWithin {
            assertEquals(InvocationContext.Mode.SEQUENTIAL, InvocationContext.mode())
        }

        TestContext.invocation(InvocationContext.Mode.CONCURRENT).executeWithin {
            assertEquals(InvocationContext.Mode.CONCURRENT, InvocationContext.mode())
        }
    }

    @Test
    fun coroutineContext() = runTest {
        TestContext.executeWithin {
            assertNull(currentCoroutineContext()[CoroutineName.Key])
        }

        val coroutineNameElement = CoroutineName("TEST-CC")
        TestContext.coroutineContext(coroutineNameElement).executeWithin {
            assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
        }
    }

    @Test
    fun testScope() = runTest {
        TestContext.executeWithin {
            assertNull(TestScopeContext.current())
        }

        val testContextWithTestScope = TestContext.testScope(isEnabled = true)
        testContextWithTestScope.executeWithin {
            assertNotNull(TestScopeContext.current())
            testContextWithTestScope.testScope(isEnabled = false).executeWithin {
                assertNull(TestScopeContext.current())
            }
        }
    }

    @Test
    fun nested() = runTest {
        val coroutineNameElement = CoroutineName("TEST-CC")
        TestContext.coroutineContext(coroutineNameElement).executeWithin {
            assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
            assertEquals(InvocationContext.Mode.SEQUENTIAL, InvocationContext.mode())
            TestContext.invocation(InvocationContext.Mode.CONCURRENT).executeWithin {
                assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
                assertEquals(InvocationContext.Mode.CONCURRENT, InvocationContext.mode())
            }
        }
    }

    @Test
    fun chained() = runTest {
        val coroutineNameElement = CoroutineName("TEST-CC")
        assertNull(currentCoroutineContext()[CoroutineName.Key])
        assertEquals(InvocationContext.Mode.SEQUENTIAL, InvocationContext.mode())
        TestContext
            .coroutineContext(coroutineNameElement)
            .invocation(InvocationContext.Mode.CONCURRENT).executeWithin {
                assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
                assertEquals(InvocationContext.Mode.CONCURRENT, InvocationContext.mode())
            }
    }
}
