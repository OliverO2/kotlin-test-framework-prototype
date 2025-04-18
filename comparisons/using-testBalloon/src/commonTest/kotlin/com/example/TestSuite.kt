package com.example

import de.infix.testBalloon.framework.AbstractTestElement
import de.infix.testBalloon.framework.testSuite
import kotlin.test.assertEquals

val TestSuite by testSuite {
    log("configuring $elementName (displayName=$displayName)")

    test("test1") {
        log("in $elementName")
        assertEquals("This test should fail!", "This test should fail?")
    }

    testSuite("inner suite") {
        test("test2") {
            log("in $elementName")
        }
    }
}

private fun AbstractTestElement.log(message: String) {
    println("$elementPath: $message\n")
}
