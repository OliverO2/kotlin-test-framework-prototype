package com.example.testLibrary

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import testFramework.Test
import testFramework.TestConfig
import testFramework.TestElement
import testFramework.TestExecutionTraversal
import testFramework.internal.printlnFixed
import testFramework.testPlatform
import testFramework.traversal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * Returns a test configuration chaining [this] with a statistics report.
 *
 * The statistics cover the test element tree rooted at the configuration's element.
 */
fun TestConfig.statisticsReport() = traversal(StatisticsReport())

private class StatisticsReport : TestExecutionTraversal {
    private val reportStart = atomic<TimeSource.Monotonic.ValueTimeMark?>(null)
    private val lock = reentrantLock()

    // These may be mutated only while `lock` is held.
    private var testCount = 0
    private var testFailureCount = 0
    private var cumulativeTestDuration = 0.seconds
    private var slowestTestDuration: Duration = Duration.ZERO
    private var slowestTestName = "(none)"
    private val threadIdsUsed = mutableSetOf<ULong>()

    override suspend fun aroundEach(testElement: TestElement, elementAction: suspend TestElement.() -> Unit) {
        val isReportRootElement = reportStart.compareAndSet(null, TimeSource.Monotonic.markNow())

        if (testElement is Test) {
            var testResult: Throwable? = null
            measureTime {
                try {
                    testElement.elementAction()
                } catch (throwable: Throwable) {
                    testResult = throwable
                }
            }.also { duration ->
                lock.withLock {
                    testCount++
                    if (testResult != null) testFailureCount++

                    cumulativeTestDuration += duration
                    if (duration > slowestTestDuration) {
                        slowestTestName = testElement.elementPath
                        slowestTestDuration = duration
                    }

                    threadIdsUsed.add(testPlatform.threadId())
                }
            }
            if (testResult != null) throw testResult
        } else {
            testElement.elementAction()
        }

        if (isReportRootElement) {
            val elapsedTime = reportStart.value!!.elapsedNow()
            printlnFixed(
                "${testElement.elementPath}[${testPlatform.displayName}]: ran $testCount test(s)" +
                    " on ${threadIdsUsed.size} thread(s) in $elapsedTime," +
                    " cumulative test duration: $cumulativeTestDuration"
            )
            if (slowestTestDuration != Duration.ZERO) {
                printlnFixed(
                    "\tThe slowest test '$slowestTestName' took $slowestTestDuration."
                )
            }
        }
    }
}
