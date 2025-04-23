package com.example

import de.infix.testBalloon.framework.internal.printlnFixed
import de.infix.testBalloon.framework.testSuite
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// Wrap a time-reporting action around all tests of this suite.

val UsingAroundAll by testSuite {
    aroundAll { testSuiteAction ->
        measureTime {
            testSuiteAction() // Never forget to actually call the action executing the test suite.
        }.also {
            printlnFixed("$testElementPath took $it") // This will show the execution time of the entire suite.
        }
    }

    test("first") {
        delay(10.milliseconds)
    }

    test("second") {
        delay(20.milliseconds)
    }
}
