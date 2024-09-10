package com.example

import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.listeners.PrepareSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class TestScope1 :
    FunSpec(
        {
            extension(object : PrepareSpecListener {
                override suspend fun prepareSpec(kclass: KClass<out Spec>) {
                    super.prepareSpec(kclass)
                    log("prepareSpec TestScope1")
                }
            })

            beforeEach { log("beforeEach TestScope1") }

            extension(object : TestCaseExtension {
                override suspend fun intercept(
                    testCase: TestCase,
                    execute: suspend (TestCase) -> TestResult
                ): TestResult {
                    log("TestCaseExtension TestScope1 start")
                    val result = withContext(CoroutineName("TestCaseExtension TestScope1")) {
                        execute(testCase)
                    }
                    log("TestCaseExtension TestScope1 end")
                    return result
                }
            })

            afterEach { log("afterEach TestScope1") }

            finalizeSpec { log("finalizeSpec TestScope1") }

            test("test1") {
                log("in TestScope1.test1 [${currentCoroutineContext()[CoroutineName]}]")
                fail("something wrong in TestScope1.test1")
            }

            context("test2") {
                beforeEach { log("beforeEach TestScope1.test2") }

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
    FunSpec(
        {
            beforeEach { log("beforeEach TestScope2") }

            finalizeSpec { log("finalizeSpec TestScope2") }

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
    FunSpec(
        {
            beforeEach { log("beforeEach TestScope3") }

            finalizeSpec { log("finalizeSpec TestScope3") }

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

fun log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $message")
    println(message)
}

fun fail(message: String): Unit = throw AssertionError(message)
