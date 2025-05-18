package de.infix.testBalloon.framework

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.milliseconds
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
    fun noTestScope() = assertSuccessfulSuite(testConfig = TestConfig.testScope(isEnabled = false)) {
        @OptIn(ExperimentalCoroutinesApi::class)
        test("test1") {
            assertFails {
                testScope.currentTime
            }.assertMessageStartsWith("Test(suite.test1) is not executing in a TestScope.")
        }
    }

    @Test
    fun launchesAreWaitedFor() = withTestFramework {
        val launchCount = 10
        val completedLaunches = ConcurrentList<Int>()

        val suite1 by testSuite("suite1", compartment = TestCompartment.RealTime) {
            test("test1") {
                repeat(launchCount) { launchIndex ->
                    launch {
                        delay(100.milliseconds)
                        completedLaunches.add(launchIndex)
                    }
                }
            }
        }

        withTestReport(suite1) {
            with(finishedTestEvents()) {
                assertEquals(
                    launchCount,
                    completedLaunches.elements().toSet().size,
                    "All launched coroutines must have completed"
                )
            }
        }
    }
}
