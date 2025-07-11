package com.example.testLibrary

import de.infix.testBalloon.framework.TestAction
import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestSuite
import de.infix.testBalloon.framework.testScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/**
 * Declares a test with a [timeout] as defined in kotlinx.coroutines [withTimeout].
 */
fun TestSuite.test(name: String, timeout: Duration, action: TestAction) =
    test(name, testConfig = TestConfig.testScope(isEnabled = false)) {
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

/**
 * Declares a test with a non-standard signature for its [action] parameter.
 *
 * To make the IDE plugin recognize the invocations of this function as tests, requires a `@TestDiscoverable`
 * annotation.
 */
@TestDiscoverable
fun TestSuite.databaseTest(name: String, action: suspend Database.() -> Unit) {
    test(name) {
        Database(this).use {
            it.action()
        }
    }
}

class Database(scope: CoroutineScope) : AutoCloseable {
    override fun close() {
        // Do stuff.
    }
}
