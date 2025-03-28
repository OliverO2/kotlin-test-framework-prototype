package com.example

import testFramework.AbstractTestElement
import testFramework.testSuite

val TestSuitePrototype by testSuite {
    log("configuring $elementName (displayName=$displayName)")

    test("test1") {
        log("in $elementName")
        fail("something wrong in $elementPath")
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

private fun fail(message: String): Unit = throw AssertionError(message)
