package com.example

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import testFramework.TestAction
import testFramework.TestSuite
import testFramework.annotations.TestSuiteDeclaration
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// @TestSessionDeclaration
// class MyTestSession : TestSession(defaultCompartment = { ParallelCompartment })

@TestSuiteDeclaration
class TestSuite1 :
    TestSuite(
        {
            // parallelism = testPlatform.parallelism

            val fixtureA = fixture { MyFirstFixture(this) } closeWith { closeSuspending() }

            aroundAll { childElementActions ->
                log("aroundAll TestSuite1 start")
                withContext(CoroutineName("aroundAll TestSuite1")) {
                    childElementActions()
                }
                log("aroundAll TestSuite1 end")
            }

            test("test1") {
                log("in TestSuite1.test1 [${currentCoroutineContext()[CoroutineName]}], A=${fixtureA()}")
                fail("something wrong in TestSuite1.test1")
            }

            suite("child-suite2") {
                val fixtureB = fixture { MySecondFixture(this) }

                aroundAll { childElementActions ->
                    log("aroundAll TestSuite1.child-suite2 start")
                    withContext(CoroutineName("aroundAll TestSuite1.child-suite2")) {
                        childElementActions()
                    }
                    log("aroundAll TestSuite1.child-suite2 end")
                }

                test("nested1") {
                    log("in TestSuite1.child-suite2.nested1 – before delay, A=${fixtureA()}, B=${fixtureB()}")
                    delay(0.3.seconds)
                    log(
                        "in TestSuite1.child-suite2.nested1 – after delay [${currentCoroutineContext()[CoroutineName]}]"
                    )
                }

                test("nested2") {
                    log("in TestSuite1.child-suite2.nested2, A=${fixtureA()}, B=${fixtureB()}")
                    fail("something wrong in TestSuite1.child-suite2.nested2")
                }
            }

            for (generationIndex in 1..3) {
                test("test3-$generationIndex") {
                    log("in TestSuite1.test3-$generationIndex – before delay, A=${fixtureA()}")
                    delay(0.2.seconds)
                    log(
                        "in TestSuite1.test3-$generationIndex – after delay [${currentCoroutineContext()[CoroutineName]}]"
                    )
                }
            }
        }
    )

@TestSuiteDeclaration
class TestSuite2 :
    TestSuite(
        // compartment = TestSession.BenchmarkCompartment,
        {
            test("!test1") {
                log("in TestSuite2.test1")
            }

            test("test2 with strange characters <&>'Ä\" and a –\t– tab", timeout = 0.1.seconds) {
                log("in TestSuite2.test2 – before delay")
                delay(0.3.seconds)
                log("in TestSuite2.test2 – after delay")
            }
        }
    )

@TestSuiteDeclaration
class TestSuite3 :
    TestSuite(
        {
            val fixtureC = fixture { MyFirstFixture(this) }

            test("!test1") {
                log("in TestSuite3.test1, C=${fixtureC()}")
            }

            test("!test2") {
                log("in TestSuite3.test2, C=${fixtureC()}")
            }
        }
    )

private data class MyFirstFixture(
    val suite: TestSuite,
    var state: String = "open",
    val incarnation: Int = incarnationCount.incrementAndGet()
) {
    init {
        suite.log("$this initializing")
    }

    suspend fun closeSuspending() {
        @OptIn(ExperimentalStdlibApi::class)
        suite.log("$this closing (suspending, dispatcher=${coroutineContext[CoroutineDispatcher]})")
        state = "closed"
    }

    override fun toString(): String = "${this::class.simpleName}(${suite.simpleElementName}, i=$incarnation, s=$state)"

    companion object {
        val incarnationCount = atomic(0)
    }
}

private data class MySecondFixture(
    val suite: TestSuite,
    var state: String = "open",
    val incarnation: Int = incarnationCount.incrementAndGet()
) : AutoCloseable {

    init {
        suite.log("$this initializing")
    }

    override fun close() {
        suite.log("$this closing (AutoCloseable)")
        state = "closed"
    }

    override fun toString(): String = "${this::class.simpleName}(${suite.simpleElementName}, i=$incarnation, s=$state)"

    companion object {
        val incarnationCount = atomic(0)
    }
}

fun TestSuite.test(name: String, timeout: Duration, action: TestAction) = test(name) {
    try {
        withTimeout(timeout) {
            action()
        }
    } catch (timeoutCancellationException: TimeoutCancellationException) {
        throw AssertionError("$timeoutCancellationException", timeoutCancellationException)
    }
}

private fun TestSuite.log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $elementName: $message")
    println("$elementName: $message\n")
}

private fun fail(message: String): Unit = throw AssertionError(message)
