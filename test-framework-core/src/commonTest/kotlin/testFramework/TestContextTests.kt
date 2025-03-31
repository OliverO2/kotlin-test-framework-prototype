package testFramework

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestConfigTests {
    @Test
    fun invocation() = runTest {
        TestConfig.executeWithin {
            assertEquals(InvocationContext.Mode.SEQUENTIAL, InvocationContext.mode())
        }

        TestConfig.invocation(InvocationContext.Mode.CONCURRENT).executeWithin {
            assertEquals(InvocationContext.Mode.CONCURRENT, InvocationContext.mode())
        }
    }

    @Test
    fun coroutineContext() = runTest {
        TestConfig.executeWithin {
            assertNull(currentCoroutineContext()[CoroutineName.Key])
        }

        val coroutineNameElement = CoroutineName("TEST-CC")
        TestConfig.coroutineContext(coroutineNameElement).executeWithin {
            assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
        }
    }

    @Test
    fun testScope() = runTest {
        TestConfig.executeWithin {
            assertNull(TestScopeContext.current())
        }

        val testConfigWithTestScope = TestConfig.testScope(isEnabled = true)
        testConfigWithTestScope.executeWithin {
            assertNotNull(TestScopeContext.current())
            testConfigWithTestScope.testScope(isEnabled = false).executeWithin {
                assertNull(TestScopeContext.current())
            }
        }
    }

    @Test
    fun nested() = runTest {
        val coroutineNameElement = CoroutineName("TEST-CC")
        TestConfig.coroutineContext(coroutineNameElement).executeWithin {
            assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
            assertEquals(InvocationContext.Mode.SEQUENTIAL, InvocationContext.mode())
            TestConfig.invocation(InvocationContext.Mode.CONCURRENT).executeWithin {
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
        TestConfig
            .coroutineContext(coroutineNameElement)
            .invocation(InvocationContext.Mode.CONCURRENT).executeWithin {
                assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
                assertEquals(InvocationContext.Mode.CONCURRENT, InvocationContext.mode())
            }
    }
}
