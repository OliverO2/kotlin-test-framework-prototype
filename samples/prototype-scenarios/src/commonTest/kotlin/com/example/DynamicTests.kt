package com.example

import testFramework.testSuite
import kotlin.test.assertEquals

// Use plain Kotlin to dynamically create a set of data-driven tests.
val Dynamic by testSuite {
    val testCases = mapOf(
        "one" to 3,
        "two" to 3,
        "three" to 5
    )

    testCases.forEach { (string, expectedLength) ->
        test("length of '$string'") {
            assertEquals(expectedLength, string.length)
        }
    }
}
