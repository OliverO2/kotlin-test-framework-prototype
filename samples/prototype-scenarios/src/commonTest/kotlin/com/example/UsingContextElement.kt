package com.example

import com.example.testLibrary.statisticsReport
import kotlinx.coroutines.delay
import testFramework.TestConfig
import testFramework.TestInvocation
import testFramework.coroutineContext
import testFramework.disable
import testFramework.dispatcherWithParallelism
import testFramework.invocation
import testFramework.testPlatform
import testFramework.testSuite
import kotlin.time.Duration.Companion.milliseconds

// Combine concurrent invocation and multithreading by chaining configuration elements.

val UsingContextElement by testSuite {
    configuration = TestConfig
        .invocation(TestInvocation.CONCURRENT)
        .coroutineContext(dispatcherWithParallelism(4))
        .statisticsReport() // a custom configuration for reporting

    // Conditionally disable the test suite on single-threaded platforms.
    if (testPlatform.parallelism < 2) {
        configuration = configuration.disable()
    }

    for (coroutineId in 1..20) {
        test("#$coroutineId") {
            delay(10.milliseconds)
        }
    }
}
