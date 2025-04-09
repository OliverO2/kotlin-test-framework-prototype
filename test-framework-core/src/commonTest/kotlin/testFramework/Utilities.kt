package testFramework

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import testFramework.internal.TestElementEvent
import testFramework.internal.TestFramework
import testFramework.internal.TestReport
import testFramework.internal.initializeTestFramework
import testFramework.internal.logInfo
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/** Configures a test suite with [content] and asserts its successful execution. */
internal fun assertSuccessfulSuite(
    testSession: AbstractTestSession? = null,
    configuration: TestConfig = TestConfig,
    content: TestSuite.() -> Unit
): TestResult = withTestFramework(testSession) {
    val suite by testSuite("suite", configuration = configuration) {
        content()
    }

    withTestReport(suite) {
        finishedTestEvents().assertAllSucceeded()
    }
}

/** Performs [action] in an initialized framework session. */
@OptIn(DelicateCoroutinesApi::class)
internal fun withTestFramework(testSession: AbstractTestSession? = null, action: suspend () -> Unit): TestResult {
    val job = GlobalScope.launch(Dispatchers.Default) {
        initializeTestFramework(testSession)
        try {
            action()
            if (testPlatform.type == TestPlatform.Type.WASM_WASI) {
                logInfo { "Primary coroutine on ${testPlatform.displayName} completed." }
            }
        } finally {
            // Reset global state for another round of test framework initialization.
            TestFramework.resetState()
        }
    }

    return TestScope().runTest(timeout = 2.minutes) {
        if (testPlatform.type == TestPlatform.Type.WASM_WASI) {
            logInfo { "WORKAROUND: Skip waiting for primary coroutine on ${testPlatform.displayName}." }
        } else {
            // WORKAROUND: `job.join()` will hang on Wasm/WASI if a `Test` is running on the test dispatcher.
            job.join()
        }
    }
}

/**
 * Configures the framework session with top-level [suites], executes it, lets [action] examine the resulting report.
 */
internal suspend fun withTestReport(
    vararg suites: TestSuite,
    selection: TestElement.Selection = TestElement.AllInSelection,
    expectFrameworkFailure: Boolean = false,
    action: suspend InMemoryTestReport.(frameworkFailure: Throwable?) -> Unit
) {
    require(suites.isNotEmpty()) { "At least one suite must be provided" }

    TestSession.global.parameterize(selection)

    val report = InMemoryTestReport()
    var frameworkFailure: Throwable? = null
    try {
        TestSession.global.execute(report)
    } catch (throwable: Throwable) {
        frameworkFailure = throwable
        if (!expectFrameworkFailure) {
            throw frameworkFailure
        }
    }
    report.action(frameworkFailure)
}

/**
 * An in-memory test report, collecting [TestElementEvent]s for later examination.
 */
internal class InMemoryTestReport : TestReport() {
    private val allEvents = ConcurrentList<TestElementEvent>()

    fun allEvents() = allEvents.elements()
    fun finishedEvents() = allEvents().mapNotNull { it as? TestElementEvent.Finished }
    fun allTestEvents() = allEvents().filter { it.element is Test }
    fun finishedTestEvents() = allTestEvents().mapNotNull { it as? TestElementEvent.Finished }

    override suspend fun add(event: TestElementEvent) {
        // println("REPORT: $event")
        allEvents.add(event)
    }

    fun List<TestElementEvent.Finished>.assertAllSucceeded() {
        if (!all { it.succeeded }) {
            fail(
                mapNotNull {
                    if (!it.succeeded) "Test '${it.element.elementName}' failed with ${it.throwable}" else null
                }.joinToString(separator = ",\n\t")
            )
        }
    }

    fun List<TestElementEvent>.assertElementPathsContainInOrder(
        expectedPaths: List<String>,
        exhaustive: Boolean = false
    ) {
        map { it.element.elementPath }.assertContainsInOrder(expectedPaths, exhaustive)
    }
}

internal fun List<String>.assertContainsInOrder(expectedElements: List<String>, exhaustive: Boolean = false) {
    if (exhaustive && expectedElements.size != size) {
        fail("Expected ${expectedElements.size} elements but got $size")
    }
    val firstExpectedElement = expectedElements.first()
    val actualElementsSlice = drop(indexOfFirst { it == firstExpectedElement }).take(expectedElements.size)
    assertContentEquals(expectedElements, actualElementsSlice)
}

internal fun Throwable.assertMessageStartsWith(phrase: String) =
    assertTrue(message?.startsWith(phrase) == true, "Exception message did not start with '$phrase', but is '$message'")

/** References a [TestSuite], resolving the lazy initialization for top-level suites. */
internal fun TestSuite.reference() {
    require(toString().isNotEmpty())
}

internal class ConcurrentSet<Element> : SynchronizedObject() {
    private val elements = mutableSetOf<Element>()

    fun add(element: Element) = synchronized(this) { elements.add(element) }
    fun elements() = synchronized(this) { elements.toSet() }
}

internal class ConcurrentList<Element> : SynchronizedObject() {
    private val elements = mutableListOf<Element>()

    fun add(element: Element) = synchronized(this) { elements.add(element) }
    fun elements() = synchronized(this) { elements.toList() }
}
