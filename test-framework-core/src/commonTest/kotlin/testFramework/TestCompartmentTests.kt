package testFramework

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class TestCompartmentTests {
    @Test
    fun defaultCompartment() = assertSuccessfulSuite {
        val compartment = parentSuite as? TestCompartment
        test("test1") {
            assertEquals(TestCompartment.Default, compartment)
        }
        test("test2") {
            assertEquals(TestCompartment.Default, compartment)
        }
    }

    @Test
    fun twoCompartments() = withTestFramework {
        val suite1 by testSuite("suite1") {
            val compartment = parentSuite as? TestCompartment
            test("test1") {
                assertEquals(TestCompartment.Default, compartment)
                assertNull(currentCoroutineContext()[CoroutineName.Key])
            }
            test("test2") {
                assertEquals(TestCompartment.Default, compartment)
                assertNull(currentCoroutineContext()[CoroutineName.Key])
            }
        }

        val coroutineName2 = CoroutineName("#2")
        val compartment2 =
            TestCompartment("Compartment 2", configuration = TestConfig.coroutineContext(coroutineName2))
        val suite2 by testSuite("suite2", compartment = compartment2) {
            val compartment = parentSuite as? TestCompartment
            test("test1") {
                assertEquals(compartment2, compartment)
                assertEquals(coroutineName2, currentCoroutineContext()[CoroutineName.Key])
            }
            test("test2") {
                assertEquals(compartment2, compartment)
                assertEquals(coroutineName2, currentCoroutineContext()[CoroutineName.Key])
            }
        }

        val suite3 by testSuite("suite3") {
            val compartment = parentSuite as? TestCompartment
            test("test1") {
                assertEquals(TestCompartment.Default, compartment)
                assertNull(currentCoroutineContext()[CoroutineName.Key])
            }
            test("test2") {
                assertEquals(TestCompartment.Default, compartment)
                assertNull(currentCoroutineContext()[CoroutineName.Key])
            }
        }

        val suite4 by testSuite("suite4", compartment = compartment2) {
            val compartment = parentSuite as? TestCompartment
            test("test1") {
                assertEquals(compartment, compartment2)
                assertEquals(coroutineName2, currentCoroutineContext()[CoroutineName.Key])
            }
            test("test2") {
                assertEquals(compartment, compartment2)
                assertEquals(coroutineName2, currentCoroutineContext()[CoroutineName.Key])
            }
        }

        withTestReport(suite1, suite2, suite3, suite4) {
            with(finishedTestEvents()) {
                assertTrue(isNotEmpty())
                assertAllSucceeded()

                // Tests in each compartment must be processed consecutively.
                assertElementPathsContainInOrder(
                    map { it.element.elementPath }.filter { it.startsWith("suite1") || it.startsWith("suite3") }
                )
                assertElementPathsContainInOrder(
                    map { it.element.elementPath }.filter { it.startsWith("suite2") || it.startsWith("suite4") }
                )
            }
        }
    }

    @Test
    fun concurrency() = withTestFramework {
        val concurrentThreadIds = ConcurrentSet<ULong>()

        val suite1 by testSuite("topSuite1", compartment = TestCompartment.Concurrent) {
            val outerSuiteThreadId = testPlatform.threadId()

            for (suiteNumber in 1..3) {
                testSuite("subSuite$suiteNumber") {
                    assertEquals(outerSuiteThreadId, testPlatform.threadId())

                    for (testNumber in 1..3) {
                        test("test$testNumber") {
                            concurrentThreadIds.add(testPlatform.threadId())
                            delay(1.milliseconds)
                        }
                    }
                }
            }
        }

        val sequentialThreadIds = ConcurrentSet<ULong>()

        val suite2 by testSuite("topSuite2") {
            for (suiteNumber in 1..3) {
                testSuite("subSuite$suiteNumber") {
                    for (testNumber in 1..3) {
                        test("test$testNumber") {
                            sequentialThreadIds.add(testPlatform.threadId())
                            delay(1.milliseconds)
                        }
                    }
                }
            }
        }

        withTestReport(suite1, suite2) {
            with(finishedTestEvents()) {
                assertTrue(isNotEmpty())
                assertAllSucceeded()

                val concurrentThreadCount = concurrentThreadIds.elements().size
                if (testPlatform.parallelism > 1) {
                    if (concurrentThreadCount <= 1) {
                        fail("Expected a concurrent thread count > 1 but was $concurrentThreadCount")
                    }
                } else {
                    assertEquals(1, concurrentThreadCount)
                }

                assertEquals(1, sequentialThreadIds.elements().size)

                // Tests in each compartment must be processed consecutively.
                assertElementPathsContainInOrder(map { it.element.elementPath }.filter { it.startsWith("topSuite1") })
                assertElementPathsContainInOrder(map { it.element.elementPath }.filter { it.startsWith("topSuite2") })
            }
        }
    }

    @Test
    fun ui() = withTestFramework {
        val uiThreadIds = ConcurrentSet<ULong>()

        val suite by testSuite("topSuite", compartment = TestCompartment.UI(Dispatchers.Unconfined)) {
            for (suiteNumber in 1..3) {
                testSuite("subSuite$suiteNumber") {
                    test("test1") {
                        uiThreadIds.add(testPlatform.threadId())
                        delay(1.milliseconds)
                        assertFails {
                            withMainDispatcher(Dispatchers.Default) {}
                        }.assertMessageStartsWith("Another invocation of withMainDispatcher() is still active.")
                    }

                    for (testNumber in 1..3) {
                        test("test$testNumber") {
                            uiThreadIds.add(testPlatform.threadId())
                            delay(1.milliseconds)
                        }
                    }
                }
            }
        }

        withTestReport(suite) {
            with(finishedTestEvents()) {
                assertTrue(isNotEmpty())
                assertAllSucceeded()
                assertEquals(1, uiThreadIds.elements().size)
            }
        }
    }
}
