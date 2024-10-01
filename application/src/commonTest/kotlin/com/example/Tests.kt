package com.example

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import testFramework.TestSuite
import testFramework.annotations.TestDeclaration
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

@TestDeclaration
internal class TestSuite1 :
    TestSuite(
        {
            // scopeParallelism = testPlatform.parallelism

            val fixtureA = fixture { MyFirstFixture(this) } closeWith { closeSuspending() }

            aroundAll { scopeAction ->
                log("aroundAll TestSuite1 start")
                withContext(CoroutineName("aroundAll TestSuite1")) {
                    scopeAction()
                }
                log("aroundAll TestSuite1 end")
            }

            test("test1") {
                log("in TestSuite1.test1 [${currentCoroutineContext()[CoroutineName]}], A=${fixtureA()}")
                fail("something wrong in TestSuite1.test1")
            }

            suite("child-suite2") {
                val fixtureB = fixture { MySecondFixture(this) }

                aroundAll { scopeAction ->
                    log("aroundAll TestSuite1.child-suite2 start")
                    withContext(CoroutineName("aroundAll TestSuite1.child-suite2")) {
                        scopeAction()
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

@TestDeclaration
internal class TestSuite2 :
    TestSuite(
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

@TestDeclaration
internal class TestSuite3 :
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

    override fun toString(): String = "${this::class.simpleName}(${suite.simpleScopeName}, i=$incarnation, s=$state)"

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

    override fun toString(): String = "${this::class.simpleName}(${suite.simpleScopeName}, i=$incarnation, s=$state)"

    companion object {
        val incarnationCount = atomic(0)
    }
}

private fun TestSuite.log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $scopeName: $message")
    println("$scopeName: $message\n")
}

private fun fail(message: String): Unit = throw AssertionError(message)
