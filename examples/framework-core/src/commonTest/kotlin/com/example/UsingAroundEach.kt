package com.example

import de.infix.testBalloon.framework.Test
import de.infix.testBalloon.framework.TestConfig
import de.infix.testBalloon.framework.aroundEach
import de.infix.testBalloon.framework.testScope
import de.infix.testBalloon.framework.testSuite
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// Make tests exceeding a time limit fail.
// `TestConfig.aroundEach` applies to each test of this suite (and tests in suites below it if there were any).

val UsingAroundEach by testSuite {
    configuration = TestConfig
        .testScope(isEnabled = false) // Disable the coroutines `TestScope` to use real time
        .aroundEach { elementAction ->
            if (this is Test) {
                measureTime {
                    elementAction() // Executes a single test.
                }.also { duration ->
                    if (duration > 50.milliseconds) {
                        throw AssertionError("Time limit exceeded. Duration was $duration.")
                    }
                }
            } else {
                elementAction() // Executes a non-Test element (a TestSuite and its derivatives).
            }
        }

    test("10 milliseconds") {
        delay(10.milliseconds)
    }

    test("20 milliseconds") {
        delay(20.milliseconds)
    }

    test("30 milliseconds") {
        delay(30.milliseconds)
    }
}
