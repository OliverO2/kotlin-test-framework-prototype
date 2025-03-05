package testFramework

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
            assertEquals(InvocationContext.Mode.SEQUENTIAL, InvocationContext.mode())
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
        testSession = object : TestSession(configuration = {
            context = TestContext.invocation(InvocationContext.Mode.CONCURRENT)
        }) {}
    ) {
        test("invocation") {
            assertEquals(InvocationContext.Mode.CONCURRENT, InvocationContext.mode())
        }
    }

    @Test
    fun onlySingleInstance() = withTestFramework {
        // `withTestFramework` has set up a `TestSession`, creating another one should fail.
        assertFailsWith<IllegalArgumentException> {
            object : TestSession(configuration = {
                context = TestContext.invocation(InvocationContext.Mode.CONCURRENT)
            }) {}
        }.assertMessageStartsWith("The module has been initialized with a TestSession before.")
    }
}
