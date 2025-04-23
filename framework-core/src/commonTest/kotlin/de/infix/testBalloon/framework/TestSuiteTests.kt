package de.infix.testBalloon.framework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class TestSuiteTests {
    @Test
    fun sequentialExecution() = withTestFramework {
        val subSuiteCount = 3
        val testCount = 3
        val expectedTestElementPaths = ConcurrentList<String>()

        val suite by testSuite("topSuite") {
            for (suiteNumber in 1..subSuiteCount) {
                testSuite("subSuite$suiteNumber") {
                    for (testNumber in 1..testCount) {
                        expectedTestElementPaths.add("topSuite.subSuite$suiteNumber.test$testNumber")
                        test("test$testNumber") {
                            delay((4 - testNumber).milliseconds)
                        }
                    }
                }
            }
        }

        withTestReport(suite) {
            with(finishedTestEvents()) {
                assertTrue(isNotEmpty())
                assertAllSucceeded()
                assertElementPathsContainInOrder(expectedTestElementPaths.elements(), exhaustive = true)
            }
        }
    }

    @Test
    fun aroundAll() = assertSuccessfulSuite(testConfig = TestConfig.testScope(isEnabled = false)) {
        val outerCoroutineName = CoroutineName("from aroundAll")
        val innerDispatcher = Dispatchers.Unconfined

        aroundAll { tests ->
            withContext(outerCoroutineName) {
                tests()
            }
        }

        for (testNumber in 1..2) {
            test("test$testNumber") {
                assertEquals(outerCoroutineName, currentCoroutineContext()[CoroutineName.Key])
                @OptIn(ExperimentalStdlibApi::class)
                assertNotEquals(innerDispatcher, currentCoroutineContext()[CoroutineDispatcher.Key])
            }
        }

        testSuite("innerSuite") {
            aroundAll { tests ->
                withContext(innerDispatcher) {
                    tests()
                }
            }

            test("test1") {
                assertEquals(outerCoroutineName, currentCoroutineContext()[CoroutineName.Key])
                @OptIn(ExperimentalStdlibApi::class)
                assertEquals(innerDispatcher, currentCoroutineContext()[CoroutineDispatcher.Key])
            }
        }
    }

    @Test
    fun aroundAllWithDisabledElements() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            aroundAll { tests ->
                trace.add("$testElementPath aroundAll begin")
                tests()
                trace.add("$testElementPath aroundAll end")
            }

            test("test1", testConfig = TestConfig.disable()) {
                trace.add(testElementPath)
            }
        }

        val suite2 by testSuite("suite2") {
            aroundAll { tests ->
                trace.add("$testElementPath aroundAll begin")
                tests()
                trace.add("$testElementPath aroundAll end")
            }

            test("test1", testConfig = TestConfig.disable()) {
                trace.add(testElementPath)
            }

            test("test2") {
                trace.add(testElementPath)
            }
        }

        val suite3 by testSuite("suite3") {
            aroundAll { tests ->
                trace.add("$testElementPath aroundAll begin")
                tests()
                trace.add("$testElementPath aroundAll end")
            }

            test("test1", testConfig = TestConfig.disable()) {
                trace.add(testElementPath)
            }

            testSuite("innerSuite") {
                test("test1", testConfig = TestConfig.disable()) {
                    trace.add(testElementPath)
                }
                test("test2") {
                    trace.add(testElementPath)
                }
            }
        }

        withTestReport(suite1, suite2, suite3) {
            assertContentEquals(
                listOf(
                    "suite2 aroundAll begin",
                    "suite2.test2",
                    "suite2 aroundAll end",
                    "suite3 aroundAll begin",
                    "suite3.innerSuite.test2",
                    "suite3 aroundAll end"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun aroundAllWithFailedTest() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            aroundAll { tests ->
                trace.add("$testElementPath aroundAll begin")
                tests()
                trace.add("$testElementPath aroundAll end")
            }

            test("test1") {
                trace.add(testElementPath)
            }

            test("test2") {
                trace.add(testElementPath)
                fail("intentionally")
            }
        }

        withTestReport(suite1) {
            with(finishedTestEvents()) {
                assertEquals(2, size)
                assertEquals("suite1.test1", this[0].element.testElementPath)
                assertTrue(this[0].succeeded)
                assertEquals("suite1.test2", this[1].element.testElementPath)
                assertTrue(this[1].failed)
            }
            assertContentEquals(
                listOf(
                    "suite1 aroundAll begin",
                    "suite1.test1",
                    "suite1.test2",
                    "suite1 aroundAll end"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun aroundAllConfig() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite(
            "suite1",
            testConfig = TestConfig.aroundAll { tests ->
                trace.add("$testElementPath aroundAll begin")
                tests()
                trace.add("$testElementPath aroundAll end")
            }
        ) {
            test("test1") {
                trace.add(testElementPath)
            }

            testSuite("innerSuite") {
                test("test1") {
                    trace.add(testElementPath)
                }
            }
        }

        withTestReport(suite1) {
            assertContentEquals(
                listOf(
                    "suite1 aroundAll begin",
                    "suite1.test1",
                    "suite1.innerSuite.test1",
                    "suite1 aroundAll end"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun aroundEach() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite(
            "suite1",
            testConfig = TestConfig.aroundEach { elementAction ->
                trace.add("$testElementPath aroundEach1.1 begin")
                elementAction()
                trace.add("$testElementPath aroundEach1.1 end")
            }.aroundEach { elementAction ->
                trace.add("$testElementPath aroundEach1.2 begin")
                elementAction()
                trace.add("$testElementPath aroundEach1.2 end")
            }
        ) {
            test("test1") {
                trace.add(testElementPath)
            }

            testSuite(
                "innerSuite",
                testConfig = TestConfig.aroundEach { elementAction ->
                    trace.add("$testElementPath aroundEach2 begin")
                    elementAction()
                    trace.add("$testElementPath aroundEach2 end")
                }
            ) {
                test("test1") {
                    trace.add(testElementPath)
                }
            }
        }

        withTestReport(suite1) {
            assertContentEquals(
                listOf(
                    "suite1 aroundEach1.1 begin",
                    "suite1 aroundEach1.2 begin",
                    "suite1.test1 aroundEach1.1 begin",
                    "suite1.test1 aroundEach1.2 begin",
                    "suite1.test1",
                    "suite1.test1 aroundEach1.2 end",
                    "suite1.test1 aroundEach1.1 end",
                    "suite1.innerSuite aroundEach1.1 begin",
                    "suite1.innerSuite aroundEach1.2 begin",
                    "suite1.innerSuite aroundEach2 begin",
                    "suite1.innerSuite.test1 aroundEach1.1 begin",
                    "suite1.innerSuite.test1 aroundEach1.2 begin",
                    "suite1.innerSuite.test1 aroundEach2 begin",
                    "suite1.innerSuite.test1",
                    "suite1.innerSuite.test1 aroundEach2 end",
                    "suite1.innerSuite.test1 aroundEach1.2 end",
                    "suite1.innerSuite.test1 aroundEach1.1 end",
                    "suite1.innerSuite aroundEach2 end",
                    "suite1.innerSuite aroundEach1.2 end",
                    "suite1.innerSuite aroundEach1.1 end",
                    "suite1 aroundEach1.2 end",
                    "suite1 aroundEach1.1 end"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun failFast() = withTestFramework {
        val suite1 by testSuite("suite1", testConfig = TestConfig.failFast(3)) {
            for (testId in 1..15) {
                test("test$testId") {
                    if (testId.mod(2) == 0) {
                        fail("expect failure")
                    }
                }
            }
        }

        // Note that since this test does not interact with the Kotlin/JS test infrastructure, it tests
        // premature completion in a JVM-like fashion (stopping to run tests after "fail fast" detection)
        // on all platforms.
        withTestReport(suite1, expectFrameworkFailure = true) { frameworkFailure ->
            assertEquals(4, (frameworkFailure as? FailFastException)?.failureCount)
            with(finishedEvents()) {
                val failedTests = filter { it.failed && it.element is de.infix.testBalloon.framework.Test }
                assertEquals(5, failedTests.size) // 4 test failures plus one FailFastException
            }
        }
    }

    @Test
    fun testFixture() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val outerFixture =
                testFixture { trace.also { it.add("$testElementPath fixture creating") } } closeWith
                    { trace.add("$testElementPath fixture closing") }

            aroundAll { tests ->
                outerFixture().add("$testElementPath aroundAll begin")
                tests()
                outerFixture().add("$testElementPath aroundAll end")
            }

            test("test1") {
                outerFixture().add(testElementPath)
            }

            testSuite("innerSuite") {
                test("test1") {
                    outerFixture().add(testElementPath)
                }
            }
        }

        withTestReport(suite1) {
            assertContentEquals(
                listOf(
                    "suite1 fixture creating",
                    "suite1 aroundAll begin",
                    "suite1.test1",
                    "suite1.innerSuite.test1",
                    "suite1 aroundAll end",
                    "suite1 fixture closing"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun testFixtureWithDisabledElements() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val suite1Fixture =
                testFixture { trace.also { it.add("$testElementPath fixture creating") } } closeWith
                    { trace.add("$testElementPath fixture closing") }

            test("test1", testConfig = TestConfig.disable()) {
                suite1Fixture().add(testElementPath)
            }
        }

        val suite2 by testSuite("suite2") {
            val suite2Fixture =
                testFixture { trace.also { it.add("$testElementPath fixture creating") } } closeWith
                    { trace.add("$testElementPath fixture closing") }

            test("test1", testConfig = TestConfig.disable()) {
                suite2Fixture().add(testElementPath)
            }

            test("test2") {
                suite2Fixture().add(testElementPath)
            }
        }

        val suite3 by testSuite("suite3") {
            val suite3Fixture =
                testFixture { trace.also { it.add("$testElementPath fixture creating") } } closeWith
                    { trace.add("$testElementPath fixture closing") }

            test("test1", testConfig = TestConfig.disable()) {
                suite3Fixture().add(testElementPath)
            }

            testSuite("innerSuite") {
                test("test1", testConfig = TestConfig.disable()) {
                    suite3Fixture().add(testElementPath)
                }
                test("test2") {
                    suite3Fixture().add(testElementPath)
                }
            }
        }

        withTestReport(suite1, suite2, suite3) {
            assertContentEquals(
                listOf(
                    "suite2 fixture creating",
                    "suite2.test2",
                    "suite2 fixture closing",
                    "suite3 fixture creating",
                    "suite3.innerSuite.test2",
                    "suite3 fixture closing"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun testFixtureWithFailedTest() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val fixture1 =
                testFixture { trace.also { it.add("$testElementPath fixture creating") } } closeWith
                    { trace.add("$testElementPath fixture closing") }

            test("test1") {
                fixture1().add(testElementPath)
            }

            test("test2") {
                fixture1().add(testElementPath)
                fail("intentionally")
            }
        }

        withTestReport(suite1) {
            with(finishedTestEvents()) {
                assertEquals(2, size)
                assertTrue(this[0].succeeded)
                assertTrue(this[1].failed)
            }
            assertContentEquals(
                listOf(
                    "suite1 fixture creating",
                    "suite1.test1",
                    "suite1.test2",
                    "suite1 fixture closing"
                ),
                trace.elements()
            )
        }
    }

    @Test
    fun testFixtureActionFailure() = withTestFramework {
        var failCount = 0
        var closeCount = 0

        val suite1 by testSuite("suite1") {
            val fixture1 =
                testFixture { fail("fixture failing intentionally (${++failCount})") } closeWith { closeCount++ }

            test("test1") {
                fixture1()
            }

            test("test2") {
                fixture1()
            }
        }

        withTestReport(suite1) {
            with(finishedTestEvents()) {
                assertEquals(2, size)
                forEach { event ->
                    assertTrue(event.failed)
                    event.throwable?.assertMessageStartsWith("fixture failing intentionally")
                }
                assertEquals(2, failCount)
                assertEquals(0, closeCount)
            }
        }
    }

    @Test
    fun testFixtureWithSetupFailures() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val fixtures = listOf(
                testFixture { trace.add("$testElementPath fixture1 creating") } closeWith
                    { trace.add("$testElementPath fixture1 closing") },
                testFixture { trace.add("$testElementPath fixture2 creating") } closeWith {
                    trace.add("$testElementPath fixture2 failing intentionally on close")
                    fail("$testElementPath fixture2 failing intentionally on close")
                },
                testFixture { trace.add("$testElementPath fixture3 creating") } closeWith {
                    trace.add("$testElementPath fixture3 failing intentionally on close")
                    fail("$testElementPath fixture3 failing intentionally on close")
                }
            )

            suspend fun TestCoroutineScope.traceWithFixtureAccess() {
                trace.add("$testElementPath begin")
                fixtures.forEach { it() }
                trace.add("$testElementPath end")
            }

            test("test1") {
                traceWithFixtureAccess()
            }

            testSuite("inner") {
                aroundAll { tests ->
                    trace.add("aroundAll $testElementPath failing intentionally")
                    fail("aroundAll $testElementPath failing intentionally")
                    tests()
                }

                test("test2") {
                    traceWithFixtureAccess()
                }
            }

            test("test2") {
                traceWithFixtureAccess()
            }
        }

        withTestReport(suite1) {
            assertContentEquals(
                listOf(
                    "suite1.test1 begin",
                    "suite1 fixture1 creating",
                    "suite1 fixture2 creating",
                    "suite1 fixture3 creating",
                    "suite1.test1 end",
                    "aroundAll suite1.inner failing intentionally",
                    "suite1.test2 begin",
                    "suite1.test2 end",
                    "suite1 fixture3 failing intentionally on close",
                    "suite1 fixture2 failing intentionally on close",
                    "suite1 fixture1 closing"
                ),
                trace.elements()
            )

            with(finishedEvents()) {
                val failures = mapNotNull { it.throwable }
                assertContentEquals(
                    listOf(
                        "aroundAll suite1.inner failing intentionally",
                        "suite1 fixture3 failing intentionally on close"
                    ),
                    failures.map {
                        it.message
                    }
                )

                val fixtureClosingFailureStackTrace = failures.last().stackTraceToString()
                if (!fixtureClosingFailureStackTrace.contains(
                        Regex("""Suppressed: \S+: suite1 fixture2 failing intentionally on close""")
                    )
                ) {
                    fail(
                        "fixture closing failure missing suppressed exception:\n" +
                            fixtureClosingFailureStackTrace.prependIndent("\t")
                    )
                }
            }
        }
    }

    @Test
    fun disabled() = assertSuccessfulSuite {
        test("test1", testConfig = TestConfig.disable()) {
            fail("test '$testElementPath' should be disabled")
        }

        test("test2") {
        }

        testSuite("middleSuite") {
            testConfig = TestConfig.disable()

            test("test1") {
                fail("test '$testElementPath' should be disabled")
            }

            testSuite("innerSuite1") {
                test("test1") {
                    fail("test '$testElementPath' should be disabled")
                }
            }
        }
    }

    @Test
    fun additionalReports() = withTestFramework {
        val eventLog = mutableListOf<String>()

        class AdditionalReport(val name: String) : TestReport() {
            override suspend fun add(event: TestElementEvent) {
                eventLog.add("$name: $event${if (!event.element.testElementIsEnabled) " [*]" else ""}")
            }
        }

        class IntentionalFailure : Error("intentional failure") {
            override fun toString(): String = "IntentionalFailure()"
        }

        val additionalReportA = AdditionalReport("A")
        val additionalReportB = AdditionalReport("B")

        val suite1 by testSuite("suite1", testConfig = TestConfig.report(additionalReportA)) {
            test("test1", testConfig = TestConfig.disable()) {
            }

            test("test2") {
            }

            testSuite("middleSuite", testConfig = TestConfig.report(additionalReportB)) {
                test("test1") {
                    throw IntentionalFailure()
                }

                testSuite("innerSuite1") {
                    testConfig = testConfig.disable()

                    test("test1") {
                    }
                }
            }
        }

        withTestReport(suite1) {
            // [*] means: disabled test element, will be reported without actual execution
            assertContentEquals(
                listOf(
                    "A: TestSuite(suite1): Starting",
                    "A: Test(suite1.test1): Starting [*]",
                    "A: Test(suite1.test1): Finished – throwable=null [*]",
                    "A: Test(suite1.test2): Starting",
                    "A: Test(suite1.test2): Finished – throwable=null",
                    "A: TestSuite(suite1.middleSuite): Starting",
                    "B: TestSuite(suite1.middleSuite): Starting",
                    "A: Test(suite1.middleSuite.test1): Starting",
                    "B: Test(suite1.middleSuite.test1): Starting",
                    "B: Test(suite1.middleSuite.test1): Finished – throwable=IntentionalFailure()",
                    "A: Test(suite1.middleSuite.test1): Finished – throwable=IntentionalFailure()",
                    "A: TestSuite(suite1.middleSuite.innerSuite1): Starting [*]",
                    "B: TestSuite(suite1.middleSuite.innerSuite1): Starting [*]",
                    "A: Test(suite1.middleSuite.innerSuite1.test1): Starting [*]",
                    "B: Test(suite1.middleSuite.innerSuite1.test1): Starting [*]",
                    "B: Test(suite1.middleSuite.innerSuite1.test1): Finished – throwable=null [*]",
                    "A: Test(suite1.middleSuite.innerSuite1.test1): Finished – throwable=null [*]",
                    "B: TestSuite(suite1.middleSuite.innerSuite1): Finished – throwable=null [*]",
                    "A: TestSuite(suite1.middleSuite.innerSuite1): Finished – throwable=null [*]",
                    "B: TestSuite(suite1.middleSuite): Finished – throwable=null",
                    "A: TestSuite(suite1.middleSuite): Finished – throwable=null",
                    "A: TestSuite(suite1): Finished – throwable=null"
                ),
                eventLog
            )
        }
    }

    @Test
    fun topLevelClassDeclarations() = withTestFramework {
        class Suite1 :
            TestSuite({
                test("(1)test1") {}
            })

        class Suite2 :
            TestSuite(
                testConfig = TestConfig,
                {
                    test("(2)test1") {}
                }
            )

        class Suite3 :
            TestSuite(
                compartment = TestCompartment.Default,
                {
                    test("(3)test1") {}
                }
            )

        class Suite4 :
            TestSuite(
                compartment = TestCompartment.Default,
                testConfig = TestConfig,
                {
                    test("(4)test1") {}
                }
            )

        class Suite5 :
            TestSuite(
                name = "Suite5",
                testConfig = TestConfig,
                {
                    test("test1") {}
                }
            )

        class Suite6 :
            TestSuite(
                name = "Suite6",
                compartment = TestCompartment.Default,
                {
                    test("test1") {}
                }
            )

        class Suite7 :
            TestSuite(
                name = "Suite7",
                compartment = TestCompartment.Default,
                testConfig = TestConfig,
                {
                    test("test1") {}
                }
            )

        withTestReport(Suite1(), Suite2(), Suite3(), Suite4(), Suite5(), Suite6(), Suite7()) {
            with(finishedTestEvents()) {
                assertAllSucceeded()
                assertElementPathsContainInOrder(
                    listOf(
                        ".(1)test1",
                        ".(2)test1",
                        ".(3)test1",
                        ".(4)test1",
                        "Suite5.test1",
                        "Suite6.test1",
                        "Suite7.test1"
                    ),
                    exhaustive = true
                )
            }
        }
    }
}
