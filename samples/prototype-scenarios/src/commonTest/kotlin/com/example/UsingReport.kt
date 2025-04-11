package com.example

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.delay
import testFramework.Test
import testFramework.TestConfig
import testFramework.TestElement
import testFramework.TestElementEvent
import testFramework.TestReport
import testFramework.disable
import testFramework.internal.printlnFixed
import testFramework.report
import testFramework.testSuite
import kotlin.time.Duration.Companion.seconds

// Use a test report to print information about disabled tests.

val UsingReport by testSuite(configuration = TestConfig.report(DisabledTestsReport())) {
    test("test1") {
        delay(1.seconds)
    }

    testSuite("innerSuite", configuration = TestConfig.disable()) {
        test("testA") {
            delay(2.seconds)
        }

        test("testB") {
            delay(3.seconds)
        }
    }
}

private class DisabledTestsReport : TestReport() {
    private val rootElement = atomic<TestElement?>(null)
    private val lock = reentrantLock()
    private val disabledTestPaths = mutableListOf<String>() // guarded by lock

    override suspend fun add(event: TestElementEvent) {
        rootElement.compareAndSet(null, event.element)

        if (event !is TestElementEvent.Finished) return

        val element = event.element

        if (!element.isEnabled && element is Test) {
            lock.withLock { disabledTestPaths.add(element.elementPath) }
        }

        if (element == rootElement.value && disabledTestPaths.isNotEmpty()) {
            printlnFixed(
                "WARNING: ${disabledTestPaths.size} disabled test(s) in ${rootElement.value?.elementPath}:\n\t" +
                    disabledTestPaths.joinToString("\n\t")
            )
        }
    }
}
