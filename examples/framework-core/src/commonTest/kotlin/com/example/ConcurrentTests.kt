package com.example

import com.example.testLibrary.statisticsReport
import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.TestInvocation
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.invocation
import de.infix.testBalloon.framework.singleThreaded
import de.infix.testBalloon.framework.testScope
import de.infix.testBalloon.framework.testSuite
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// Disabling the coroutines `TestScope` at the top-level suite makes everything run on real time.
val SequentialVsConcurrent by testSuite(testConfig = TestConfig.testScope(isEnabled = false)) {

    // Compare test runs with different concurrency settings.
    // Use our own custom TestConfig.statisticsReport() to report results for each suite.

    testSuite(
        "sequential",
        testConfig = TestConfig.invocation(TestInvocation.SEQUENTIAL).statisticsReport()
    ) {
        testSeries()
    }

    testSuite(
        "concurrent (default)",
        testConfig = TestConfig.invocation(TestInvocation.CONCURRENT).statisticsReport()
    ) {
        testSeries()
    }

    testSuite(
        "concurrent (single-threaded)",
        testConfig = TestConfig.invocation(TestInvocation.CONCURRENT).singleThreaded().statisticsReport()
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
