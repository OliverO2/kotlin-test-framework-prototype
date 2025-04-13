package com.example.testLibrary

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import testFramework.TestAction
import testFramework.TestConfig
import testFramework.TestSuite
import testFramework.testScope
import kotlin.time.Duration

/**
 * Declares a test with a [timeout] as defined in kotlinx.coroutines [withTimeout].
 */
fun TestSuite.test(name: String, timeout: Duration, action: TestAction) =
    test(name, configuration = TestConfig.testScope(isEnabled = false)) {
        try {
            withTimeout(timeout) {
                action()
            }
        } catch (timeoutCancellationException: TimeoutCancellationException) {
            throw AssertionError("$timeoutCancellationException", timeoutCancellationException)
        }
    }

/**
 * Declares a test series with a number of [iterations].
 */
fun TestSuite.test(name: String, iterations: Int, action: TestAction) {
    for (iteration in 1..iterations) {
        test("$name#$iteration") {
            action()
        }
    }
}
