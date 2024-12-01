package com.example

import kotlin.test.Test

class TestSuiteKotlinTest {
    @Test
    fun test1() {
        log("in TestSuite1KotlinTest.test1")
        fail("something wrong in TestSuite1KotlinTest.test1")
    }

    @Test
    fun test2() {
        log("in TestSuite1.test2")
    }
}

private fun log(message: String) {
    println("$message\n")
}

private fun fail(message: String): Unit = throw AssertionError(message)
