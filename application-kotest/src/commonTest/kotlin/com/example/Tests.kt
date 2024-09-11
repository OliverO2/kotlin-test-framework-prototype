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

class TestSuite1 :
    FunSpec(
        {
            extension(object : PrepareSpecListener {
                override suspend fun prepareSpec(kclass: KClass<out Spec>) {
                    super.prepareSpec(kclass)
                    log("prepareSpec TestSuite1")
                }
            })

            beforeEach { log("beforeEach TestSuite1") }

            extension(object : TestCaseExtension {
                override suspend fun intercept(
                    testCase: TestCase,
                    execute: suspend (TestCase) -> TestResult
                ): TestResult {
                    log("TestCaseExtension TestSuite1 start")
                    val result = withContext(CoroutineName("TestCaseExtension TestSuite1")) {
                        execute(testCase)
                    }
                    log("TestCaseExtension TestSuite1 end")
                    return result
                }
            })

            afterEach { log("afterEach TestSuite1") }

            finalizeSpec { log("finalizeSpec TestSuite1") }

            test("test1") {
                log("in TestSuite1.test1 [${currentCoroutineContext()[CoroutineName]}]")
                fail("something wrong in TestSuite1.test1")
            }

            context("context2") {
                beforeEach { log("beforeEach TestSuite1.test2") }

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
    FunSpec(
        {
            beforeEach { log("beforeEach TestSuite2") }

            finalizeSpec { log("finalizeSpec TestSuite2") }

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
    FunSpec(
        {
            beforeEach { log("beforeEach TestSuite3") }

            finalizeSpec { log("finalizeSpec TestSuite3") }

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

fun log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $message")
    println(message)
}

fun fail(message: String): Unit = throw AssertionError(message)
