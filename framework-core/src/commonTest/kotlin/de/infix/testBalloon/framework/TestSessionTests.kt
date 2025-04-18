package de.infix.testBalloon.framework

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestSessionTests {
    @Test
    fun defaultConfiguration() = assertSuccessfulSuite {
        test("invocation") {
            assertEquals(TestInvocation.SEQUENTIAL, TestInvocation.current())
        }
        test("coroutineContext") {
            assertNull(currentCoroutineContext()[CoroutineName.Key])
        }
        test("testScope") {
            assertNotNull(TestScopeContext.current())
        }
    }

    @Test
    fun customConfiguration() = assertSuccessfulSuite(
        testSession = object : TestSession(configuration = TestConfig.invocation(TestInvocation.CONCURRENT)) {}
    ) {
        test("invocation") {
            assertEquals(TestInvocation.CONCURRENT, TestInvocation.current())
        }
    }

    @Test
    fun onlySingleInstance() = withTestFramework {
        // `withTestFramework` has set up a `TestSession`, creating another one should fail.
        assertFailsWith<IllegalArgumentException> {
            object : TestSession(configuration = TestConfig.invocation(TestInvocation.CONCURRENT)) {}
        }.assertMessageStartsWith("The module has been initialized with a TestSession before.")
    }
}
