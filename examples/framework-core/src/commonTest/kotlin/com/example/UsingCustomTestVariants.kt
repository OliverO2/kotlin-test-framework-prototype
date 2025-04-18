package com.example

import com.example.testLibrary.test
import de.infix.testBalloon.framework.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// Use your own custom test functions, as shown here for timeouts and multiple test iterations.

val UsingCustomTestVariants by testSuite {
    test("string length", timeout = 2.seconds) {
        assertEquals(8, "Test me!".length)
    }

    test("true iterations", iterations = 10) {
        assertTrue(true)
    }
}
