package testFramework

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
    fun aroundAll() = assertSuccessfulSuite(configuration = TestConfig.testScope(isEnabled = false)) {
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
                trace.add("$elementPath aroundAll begin")
                tests()
                trace.add("$elementPath aroundAll end")
            }

            test("test1", configuration = TestConfig.disable()) {
                trace.add(elementPath)
            }
        }

        val suite2 by testSuite("suite2") {
            aroundAll { tests ->
                trace.add("$elementPath aroundAll begin")
                tests()
                trace.add("$elementPath aroundAll end")
            }

            test("test1", configuration = TestConfig.disable()) {
                trace.add(elementPath)
            }

            test("test2") {
                trace.add(elementPath)
            }
        }

        val suite3 by testSuite("suite3") {
            aroundAll { tests ->
                trace.add("$elementPath aroundAll begin")
                tests()
                trace.add("$elementPath aroundAll end")
            }

            test("test1", configuration = TestConfig.disable()) {
                trace.add(elementPath)
            }

            testSuite("innerSuite") {
                test("test1", configuration = TestConfig.disable()) {
                    trace.add(elementPath)
                }
                test("test2") {
                    trace.add(elementPath)
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
                trace.add("$elementPath aroundAll begin")
                tests()
                trace.add("$elementPath aroundAll end")
            }

            test("test1") {
                trace.add(elementPath)
            }

            test("test2") {
                trace.add(elementPath)
                fail("intentionally")
            }
        }

        withTestReport(suite1) {
            with(finishedTestEvents()) {
                assertEquals(2, size)
                assertEquals("suite1.test1", this[0].element.elementPath)
                assertTrue(this[0].succeeded)
                assertEquals("suite1.test2", this[1].element.elementPath)
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
            configuration = TestConfig.aroundAll { tests ->
                trace.add("$elementPath aroundAll begin")
                tests()
                trace.add("$elementPath aroundAll end")
            }
        ) {
            test("test1") {
                trace.add(elementPath)
            }

            testSuite("innerSuite") {
                test("test1") {
                    trace.add(elementPath)
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
            configuration = TestConfig.aroundEach { elementAction ->
                trace.add("$elementPath aroundEach1.1 begin")
                elementAction()
                trace.add("$elementPath aroundEach1.1 end")
            }.aroundEach { elementAction ->
                trace.add("$elementPath aroundEach1.2 begin")
                elementAction()
                trace.add("$elementPath aroundEach1.2 end")
            }
        ) {
            test("test1") {
                trace.add(elementPath)
            }

            testSuite(
                "innerSuite",
                configuration = TestConfig.aroundEach { elementAction ->
                    trace.add("$elementPath aroundEach2 begin")
                    elementAction()
                    trace.add("$elementPath aroundEach2 end")
                }
            ) {
                test("test1") {
                    trace.add(elementPath)
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
        val suite1 by testSuite("suite1", configuration = TestConfig.failFast(3)) {
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
                val failedTests = filter { it.failed && it.element is testFramework.Test }
                assertEquals(5, failedTests.size) // 4 test failures plus one FailFastException
            }
        }
    }

    @Test
    fun fixture() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val outerFixture =
                fixture { trace.also { it.add("$elementPath fixture creating") } } closeWith
                    { trace.add("$elementPath fixture closing") }

            aroundAll { tests ->
                outerFixture().add("$elementPath aroundAll begin")
                tests()
                outerFixture().add("$elementPath aroundAll end")
            }

            test("test1") {
                outerFixture().add(elementPath)
            }

            testSuite("innerSuite") {
                test("test1") {
                    outerFixture().add(elementPath)
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
    fun fixtureWithDisabledElements() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val suite1Fixture =
                fixture { trace.also { it.add("$elementPath fixture creating") } } closeWith
                    { trace.add("$elementPath fixture closing") }

            test("test1", configuration = TestConfig.disable()) {
                suite1Fixture().add(elementPath)
            }
        }

        val suite2 by testSuite("suite2") {
            val suite2Fixture =
                fixture { trace.also { it.add("$elementPath fixture creating") } } closeWith
                    { trace.add("$elementPath fixture closing") }

            test("test1", configuration = TestConfig.disable()) {
                suite2Fixture().add(elementPath)
            }

            test("test2") {
                suite2Fixture().add(elementPath)
            }
        }

        val suite3 by testSuite("suite3") {
            val suite3Fixture =
                fixture { trace.also { it.add("$elementPath fixture creating") } } closeWith
                    { trace.add("$elementPath fixture closing") }

            test("test1", configuration = TestConfig.disable()) {
                suite3Fixture().add(elementPath)
            }

            testSuite("innerSuite") {
                test("test1", configuration = TestConfig.disable()) {
                    suite3Fixture().add(elementPath)
                }
                test("test2") {
                    suite3Fixture().add(elementPath)
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
    fun fixtureWithFailedTest() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val fixture1 =
                fixture { trace.also { it.add("$elementPath fixture creating") } } closeWith
                    { trace.add("$elementPath fixture closing") }

            test("test1") {
                fixture1().add(elementPath)
            }

            test("test2") {
                fixture1().add(elementPath)
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
    fun fixtureActionFailure() = withTestFramework {
        var failCount = 0
        var closeCount = 0

        val suite1 by testSuite("suite1") {
            val fixture1 = fixture { fail("fixture failing intentionally (${++failCount})") } closeWith { closeCount++ }

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
    fun fixtureWithSetupFailures() = withTestFramework {
        val trace = ConcurrentList<String>()

        val suite1 by testSuite("suite1") {
            val fixtures = listOf(
                fixture { trace.add("$elementPath fixture1 creating") } closeWith
                    { trace.add("$elementPath fixture1 closing") },
                fixture { trace.add("$elementPath fixture2 creating") } closeWith {
                    trace.add("$elementPath fixture2 failing intentionally on close")
                    fail("$elementPath fixture2 failing intentionally on close")
                },
                fixture { trace.add("$elementPath fixture3 creating") } closeWith {
                    trace.add("$elementPath fixture3 failing intentionally on close")
                    fail("$elementPath fixture3 failing intentionally on close")
                }
            )

            suspend fun TestCoroutineScope.traceWithFixtureAccess() {
                trace.add("$elementPath begin")
                fixtures.forEach { it() }
                trace.add("$elementPath end")
            }

            test("test1") {
                traceWithFixtureAccess()
            }

            testSuite("inner") {
                aroundAll { tests ->
                    trace.add("aroundAll $elementPath failing intentionally")
                    fail("aroundAll $elementPath failing intentionally")
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
        test("test1", configuration = TestConfig.disable()) {
            fail("test '$elementPath' should be disabled")
        }

        test("test2") {
        }

        testSuite("middleSuite") {
            configuration = TestConfig.disable()

            test("test1") {
                fail("test '$elementPath' should be disabled")
            }

            testSuite("innerSuite1") {
                test("test1") {
                    fail("test '$elementPath' should be disabled")
                }
            }
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
                configuration = TestConfig,
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
                configuration = TestConfig,
                {
                    test("(4)test1") {}
                }
            )

        class Suite5 :
            TestSuite(
                name = "Suite5",
                configuration = TestConfig,
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
                configuration = TestConfig,
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
