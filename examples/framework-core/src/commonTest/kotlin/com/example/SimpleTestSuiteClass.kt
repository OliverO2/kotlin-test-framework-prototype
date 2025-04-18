package com.example

import de.infix.testBalloon.framework.TestSuite
import kotlin.test.assertEquals

// Experimental: While you can use a class to declare a top-level test suite, this requires more boilerplate.
// Using this feature is not recommended, and the framework may drop it.
class SimpleTestSuiteClass :
    TestSuite(
        {
            test("string length") {
                assertEquals(8, "Test me!".length)
            }
        }
    )
