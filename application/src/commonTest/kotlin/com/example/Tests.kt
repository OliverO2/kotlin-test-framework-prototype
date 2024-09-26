package com.example

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import testFramework.BasicTestSuite
import testFramework.TestSuite
import kotlin.time.Duration.Companion.seconds

internal class TestSuite1 :
    TestSuite<MyFixture>(
        {
            // scopeParallelism = testPlatform.parallelism

            fixtureForAll { MyFixture(this) }

            aroundAll { scopeAction ->
                log("aroundAll TestSuite1 start")
                withContext(CoroutineName("aroundAll TestSuite1")) {
                    scopeAction()
                }
                log("aroundAll TestSuite1 end")
            }

            test("test1") {
                log("in TestSuite1.test1 [${currentCoroutineContext()[CoroutineName]}], ${fixture()}")
                fail("something wrong in TestSuite1.test1")
            }

            suite("child-suite2") {
                fixtureForAll { MyFixture(this) }

                aroundAll { scopeAction ->
                    log("aroundAll TestSuite1.child-suite2 start")
                    withContext(CoroutineName("aroundAll TestSuite1")) {
                        scopeAction()
                    }
                    log("aroundAll TestSuite1.child-suite2 end")
                }

                test("nested1") {
                    log("in TestSuite1.child-suite2.nested1 – before delay, ${fixture()}")
                    delay(0.3.seconds)
                    log("in TestSuite1.child-suite2.nested1 – after delay")
                }

                test("nested2") {
                    log("in TestSuite1.child-suite2.nested2, ${fixture()}")
                    fail("something wrong in TestSuite1.child-suite2.nested2")
                }
            }

            for (generationIndex in 1..3) {
                test("test3-$generationIndex") {
                    log("in TestSuite1.test3-$generationIndex – before delay, ${fixture()}")
                    delay(0.2.seconds)
                    log("in TestSuite1.test3-$generationIndex – after delay")
                }
            }
        }
    )

internal class TestSuite2 :
    BasicTestSuite(
        {
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

internal class TestSuite3 :
    TestSuite<MyFixture>(
        {
            fixtureForAll { MyFixture(this) }

            test("!test1") {
                log("in TestSuite3.test1, ${fixture()}")
            }

            test("!test2") {
                log("in TestSuite3.test2, ${fixture()}")
            }
        }
    )

internal data class MyFixture(
    val suite: TestSuite<*>,
    var state: String = "open",
    val incarnation: Int = incarnationCount.incrementAndGet()
) : AutoCloseable {
    init {
        suite.log("$this initializing")
    }

    override fun close() {
        suite.log("$this closing")
        state = "closed"
    }

    override fun toString(): String = "${this::class.simpleName}(${suite.simpleScopeName}, i=$incarnation, s=$state)"

    companion object {
        val incarnationCount = atomic(0)
    }
}

private fun TestSuite<*>.log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $scopeName: $message")
    println("$scopeName: $message\n")
}

private fun fail(message: String): Unit = throw AssertionError(message)
