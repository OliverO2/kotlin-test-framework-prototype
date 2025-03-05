package com.example

import testFramework.TestCoroutineScope
import testFramework.testSuite
import kotlin.getValue

private val trace = Trace<String>()

val TestSuiteTests by testSuite {
    testSuite("fixtureWithSetupFailures") {
        testSuite("suite1") {
            val fixtures = listOf(
                fixture { trace.also { it.add("$elementPath fixture1 creating") } } closeWith
                    { trace.add("$elementPath fixture1 closing") },
                fixture { trace.also { it.add("$elementPath fixture2 creating") } } closeWith {
                    trace.add("$elementPath fixture2 failing intentionally on close")
                    fail("$elementPath fixture2 failing intentionally on close")
                },
                fixture { trace.also { it.add("$elementPath fixture3 creating") } } closeWith {
                    trace.add("$elementPath fixture3 failing intentionally on close")
                    fail("$elementPath fixture3 failing intentionally on close")
                }
            )

            suspend fun TestCoroutineScope.traceWithFixtureAccess() {
                trace.add("$elementPath begin")
                fixtures.forEach { it() }
                trace.add("$elementPath end")
            }

            test("test1") {
                traceWithFixtureAccess()
            }

            testSuite("inner") {
                aroundAll { tests ->
                    trace.add("aroundAll $elementPath failing intentionally")
                    fail("aroundAll $elementPath failing intentionally")
                    tests()
                }

                test("test2") {
                    traceWithFixtureAccess()
                }
            }

            test("test2") {
                traceWithFixtureAccess()
            }
        }
    }
}

private class Trace<Value> {
    fun add(value: Value) = println("$value")
}

private fun fail(message: String? = null): Nothing = throw AssertionError(message)
