package com.example

import kotlin.test.Test
import kotlin.test.assertEquals

class TestSuiteKotlinTest {
    @Test
    fun test1() {
        log("in TestSuite1KotlinTest.test1")
        assertEquals("This test should fail!", "This test should fail?")
    }

    @Test
    fun test2() {
        log("in TestSuiteKotlinTest.test2")
    }
}

private fun log(message: String) {
    println("$message\n")
}
