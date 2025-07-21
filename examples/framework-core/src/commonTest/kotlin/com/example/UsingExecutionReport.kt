package com.example

import de.infix.testBalloon.framework.Test
import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.TestElement
import de.infix.testBalloon.framework.TestElementEvent
import de.infix.testBalloon.framework.TestExecutionReport
import de.infix.testBalloon.framework.disable
import de.infix.testBalloon.framework.internal.printlnFixed
import de.infix.testBalloon.framework.report
import de.infix.testBalloon.framework.testSuite
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

// Use a test report to print information about disabled tests.

val UsingReport by testSuite(testConfig = TestConfig.report(DisabledTestsExecutionReport())) {
    test("test1") {
        delay(1.seconds)
    }

    testSuite("innerSuite", testConfig = TestConfig.disable()) {
        test("testA") {
            delay(2.seconds)
        }

        test("testB") {
            delay(3.seconds)
        }
    }
}

private class DisabledTestsExecutionReport : TestExecutionReport() {
    private val rootElement = atomic<TestElement?>(null)
    private val lock = reentrantLock()
    private val disabledTestPaths = mutableListOf<String>() // guarded by lock

    override suspend fun add(event: TestElementEvent) {
        rootElement.compareAndSet(null, event.element)

        if (event !is TestElementEvent.Finished) return

        val element = event.element

        if (!element.testElementIsEnabled && element is Test) {
            lock.withLock { disabledTestPaths.add(element.testElementPath) }
        }

        if (element == rootElement.value && disabledTestPaths.isNotEmpty()) {
            printlnFixed(
                "WARNING: ${disabledTestPaths.size} disabled test(s) in ${rootElement.value?.testElementPath}:\n\t" +
                    disabledTestPaths.joinToString("\n\t")
            )
        }
    }
}
