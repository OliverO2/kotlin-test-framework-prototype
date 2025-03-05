package testFramework

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds

class TestTests {
    @Test
    fun testScope() = assertSuccessfulSuite {
        @OptIn(ExperimentalCoroutinesApi::class)
        test("test1") {
            assertEquals(0, testScope.currentTime)
            delay(1.seconds)
            assertEquals(1000, testScope.currentTime)
        }
    }

    @Test
    fun noTestScope() = assertSuccessfulSuite(configuration = { context = TestContext.testScope(isEnabled = false) }) {
        @OptIn(ExperimentalCoroutinesApi::class)
        test("test1") {
            assertFails {
                testScope.currentTime
            }.assertMessageStartsWith("Test(suite.test1) is not executing in a TestScope.")
        }
    }
}
