package testFramework

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestConfigTests {
    @Test
    fun coroutineContext() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        TestConfig.executeWrapped(testSuite) {
            assertNull(currentCoroutineContext()[CoroutineName.Key])
        }

        val coroutineNameElement = CoroutineName("TEST-CC")
        TestConfig.coroutineContext(coroutineNameElement).executeWrapped(testSuite) {
            assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
        }
    }

    @Test
    fun invocation() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        TestConfig.executeWrapped(testSuite) {
            assertEquals(TestInvocation.SEQUENTIAL, TestInvocation.current())
        }

        TestConfig.invocation(TestInvocation.CONCURRENT).executeWrapped(testSuite) {
            assertEquals(TestInvocation.CONCURRENT, TestInvocation.current())
        }
    }

    @Test
    fun testScope() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        TestConfig.executeWrapped(testSuite) {
            assertNull(TestScopeContext.current())
        }

        val testConfigWithTestScope = TestConfig.testScope(isEnabled = true)
        testConfigWithTestScope.executeWrapped(testSuite) {
            assertNotNull(TestScopeContext.current())
            testConfigWithTestScope.testScope(isEnabled = false).executeWrapped(testSuite) {
                assertNull(TestScopeContext.current())
            }
        }
    }

    @Test
    fun inheritance() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        val coroutineNameElement = CoroutineName("TEST-CC")
        TestConfig.coroutineContext(coroutineNameElement).executeWrapped(testSuite) {
            assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
            assertEquals(TestInvocation.SEQUENTIAL, TestInvocation.current())
            TestConfig.invocation(TestInvocation.CONCURRENT).executeWrapped(testSuite) {
                assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
                assertEquals(TestInvocation.CONCURRENT, TestInvocation.current())
            }
        }
    }

    @Test
    fun chaining() = withTestFramework {
        val testSuite by testSuite("testSuite") {}

        val coroutineNameElement = CoroutineName("TEST-CC")
        assertNull(currentCoroutineContext()[CoroutineName.Key])
        assertEquals(TestInvocation.SEQUENTIAL, TestInvocation.current())
        TestConfig
            .coroutineContext(coroutineNameElement)
            .invocation(TestInvocation.CONCURRENT).executeWrapped(testSuite) {
                assertEquals(coroutineNameElement, currentCoroutineContext()[CoroutineName.Key])
                assertEquals(TestInvocation.CONCURRENT, TestInvocation.current())
            }
    }
}
