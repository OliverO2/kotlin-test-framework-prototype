package com.example

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import testFramework.TestModule
import testFramework.TestScope
import kotlin.time.Duration.Companion.seconds

class TestScope1 :
    TestScope(
        {
            beforeFirstScope { log("beforeFirstScope TestScope1") }

            beforeEachScope { log("beforeEachScope TestScope1") }

            aroundEachScope { scopeAction ->
                log("aroundEachScope TestScope1 start")
                withContext(CoroutineName("aroundEachScope TestScope1")) {
                    scopeAction()
                }
                log("aroundEachScope TestScope1 end")
            }

            afterEachScope { log("afterEachScope TestScope1") }

            afterLastScope { log("afterLastScope TestScope1") }

            test("test1") {
                log("in TestScope1.test1 [${currentCoroutineContext()[CoroutineName]}]")
                fail("something wrong in TestScope1.test1")
            }

            scope("test2") {
                beforeEachScope { log("beforeEachScope TestScope1.test2") }

                test("nested1") {
                    log("in TestScope1.test2.nested1 – before delay")
                    delay(0.3.seconds)
                    log("in TestScope1.test2.nested1 – after delay")
                }

                test("nested2") {
                    log("in TestScope1.test2.nested2")
                    fail("something wrong in TestScope1.test2.nested2")
                }
            }

            for (generationIndex in 1..3) {
                test("test3-$generationIndex") {
                    log("in TestScope1.test3-$generationIndex – before delay")
                    delay(0.2.seconds)
                    log("in TestScope1.test3-$generationIndex – after delay")
                }
            }
        }
    )

class TestScope2 :
    TestScope(
        module = TestModule.sequential,
        {
            beforeEachScope { log("beforeEachScope TestScope2") }

            afterLastScope { log("afterLastScope TestScope2") }

            test("!test1") {
                log("in TestScope2.test1")
            }

            test("test2 with strange characters <&>'Ä\" and a –\t– tab") {
                log("in TestScope2.test2 – before delay")
                delay(0.1.seconds)
                log("in TestScope2.test2 – after delay")
            }
        }
    )

class TestScope3 :
    TestScope(
        module = TestModule.singleThreaded,
        {
            beforeEachScope { log("beforeEachScope TestScope3") }

            afterLastScope { log("afterLastScope TestScope3") }

            test("!test1") {
                log("in TestScope3.test1")
            }

            test("test2") {
                log("in TestScope3.test2 – before delay")
                delay(0.2.seconds)
                log("in TestScope3.test2 – after delay")
            }

            test("test3") {
                log("in TestScope3.test3 – before delay")
                delay(0.2.seconds)
                log("in TestScope3.test3 – after delay")
            }
        }
    )

fun TestScope.log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $scopeName: $message")
    println("$scopeName: $message\n")
}

fun fail(message: String): Unit = throw AssertionError(message)
