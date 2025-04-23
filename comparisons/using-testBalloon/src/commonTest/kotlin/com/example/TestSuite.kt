package com.example

import de.infix.testBalloon.framework.AbstractTestElement
import de.infix.testBalloon.framework.testSuite
import kotlin.test.assertEquals

val TestSuite by testSuite {
    log("configuring $testElementName (displayName=$testElementDisplayName)")

    test("test1") {
        log("in $testElementName")
        assertEquals("This test should fail!", "This test should fail?")
    }

    testSuite("inner suite") {
        test("test2") {
            log("in $testElementName")
        }
    }
}

private fun AbstractTestElement.log(message: String) {
    println("$testElementPath: $message\n")
}
