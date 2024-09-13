package com.example

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import testFramework.TestModule
import testFramework.TestSuite
import kotlin.time.Duration.Companion.seconds

class TestSuite1 :
    TestSuite(
        {
            beforeFirstScope { log("beforeFirstScope TestSuite1") }

            aroundAllScopes { scopeAction ->
                log("aroundAllScopes TestSuite1 start")
                scopeAction()
                log("aroundAllScopes TestSuite1 end")
            }

            beforeEachScope { log("beforeEachScope TestSuite1") }

            aroundEachScope { scopeAction ->
                log("aroundEachScope TestSuite1 start")
                withContext(CoroutineName("aroundEachScope TestSuite1")) {
                    scopeAction()
                }
                log("aroundEachScope TestSuite1 end")
            }

            afterEachScope { log("afterEachScope TestSuite1") }

            afterLastScope { log("afterLastScope TestSuite1") }

            test("test1") {
                log("in TestSuite1.test1 [${currentCoroutineContext()[CoroutineName]}]")
                fail("something wrong in TestSuite1RE.test1")
            }

            suite("child-suite2") {
                beforeEachScope { log("beforeEachScope TestSuite1.test2") }

                test("nested1") {
                    log("in TestSuite1.test2.nested1 – before delay")
                    delay(0.3.seconds)
                    log("in TestSuite1.test2.nested1 – after delay")
                }

                test("nested2") {
                    log("in TestSuite1.test2.nested2")
                    fail("something wrong in TestSuite1.test2.nested2")
                }
            }

            for (generationIndex in 1..3) {
                test("test3-$generationIndex") {
                    log("in TestSuite1.test3-$generationIndex – before delay")
                    delay(0.2.seconds)
                    log("in TestSuite1.test3-$generationIndex – after delay")
                }
            }
        }
    )

class TestSuite2 :
    TestSuite(
        module = TestModule.sequential,
        {
            beforeEachScope { log("beforeEachScope TestSuite2") }

            afterLastScope { log("afterLastScope TestSuite2") }

            test("!test1") {
                log("in TestSuite2.test1")
            }

            test("test2 with strange characters <&>'Ä\" and a –\t– tab") {
                log("in TestSuite2.test2 – before delay")
                delay(0.1.seconds)
                log("in TestSuite2.test2 – after delay")
            }
        }
    )

class TestSuite3 :
    TestSuite(
        module = TestModule.singleThreaded,
        {
            beforeEachScope { log("beforeEachScope TestSuite3") }

            afterLastScope { log("afterLastScope TestSuite3") }

            test("!test1") {
                log("in TestSuite3.test1")
            }

            test("test2") {
                log("in TestSuite3.test2 – before delay")
                delay(0.2.seconds)
                log("in TestSuite3.test2 – after delay")
            }

            test("test3") {
                log("in TestSuite3.test3 – before delay")
                delay(0.2.seconds)
                log("in TestSuite3.test3 – after delay")
            }
        }
    )

fun TestSuite.log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $scopeName: $message")
    println("$scopeName: $message\n")
}

fun fail(message: String): Unit = throw AssertionError(message)
