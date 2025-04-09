package com.example

import com.example.testLibrary.statisticsReport
import kotlinx.coroutines.delay
import testFramework.TestConfig
import testFramework.TestInvocation
import testFramework.TestSuite
import testFramework.invocation
import testFramework.singleThreaded
import testFramework.testScope
import testFramework.testSuite
import kotlin.time.Duration.Companion.milliseconds

// Disabling the coroutines `TestScope` at the top-level suite makes everything run on real time.
val SequentialVsConcurrent by testSuite(configuration = TestConfig.testScope(isEnabled = false)) {

    // Compare test runs with different concurrency settings.
    // Use our own custom TestConfig.statisticsReport() to report results for each suite.

    testSuite(
        "sequential",
        configuration = TestConfig.invocation(TestInvocation.SEQUENTIAL).statisticsReport()
    ) {
        testSeries()
    }

    testSuite(
        "concurrent (default)",
        configuration = TestConfig.invocation(TestInvocation.CONCURRENT).statisticsReport()
    ) {
        testSeries()
    }

    testSuite(
        "concurrent (single-threaded)",
        configuration = TestConfig.invocation(TestInvocation.CONCURRENT).singleThreaded().statisticsReport()
    ) {
        testSeries()
    }
}

// Define your own test series builder.
private fun TestSuite.testSeries() {
    for (testId in 1..10) {
        test("#$testId") {
            delay(10.milliseconds)
        }
    }
}
