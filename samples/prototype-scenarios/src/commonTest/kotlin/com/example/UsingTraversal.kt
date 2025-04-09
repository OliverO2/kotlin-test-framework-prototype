package com.example

import com.example.testLibrary.statisticsReport
import kotlinx.coroutines.delay
import testFramework.TestConfig
import testFramework.testSuite
import kotlin.time.Duration.Companion.seconds

// Collect and report information via a custom traversal object visiting elements of a test tree.

val UsingTraversal by testSuite(configuration = TestConfig.statisticsReport()) {
    test("1 second") {
        delay(1.seconds)
    }

    test("2 seconds") {
        delay(2.seconds)
    }

    test("3 seconds") {
        delay(3.seconds)
    }
}
