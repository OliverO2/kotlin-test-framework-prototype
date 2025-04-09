package com.example

import com.example.testLibrary.statisticsReport
import com.example.testLibrary.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import testFramework.TestCompartment
import testFramework.TestConfig
import testFramework.internal.printlnFixed
import testFramework.testPlatform
import testFramework.testScope
import testFramework.testSuite
import kotlin.time.Duration.Companion.milliseconds

// Declare a suite capable of running tests concurrently.
// Compartments ensure that this does not interfere with tests requiring the default sequential execution.

val concurrentSuite by testSuite(
    compartment = TestCompartment.Concurrent,
    configuration = TestConfig
        .testScope(isEnabled = false)
        .statisticsReport()
) {
    test(iterations = 10) {
        delay(10.milliseconds)
    }
}

// Declare a suite for UI tests. This will combine sequential execution with the presence of a Main dispatcher.

val uiSuite by testSuite(
    compartment = TestCompartment.UI(),
    configuration = TestConfig.statisticsReport()
) {
    test("On UI thread") {
        launch(Dispatchers.Main) {
            delay(10.milliseconds)
            printlnFixed(testPlatform.threadDisplayName())
        }
    }
}

// Note that we can also make the entire test session run tests concurrently:
//     class MyTestSession : TestSession(defaultCompartment = { TestCompartment.Concurrent })
