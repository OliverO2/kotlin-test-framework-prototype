@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.example

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import testFramework.AbstractTestElement
import testFramework.InvocationContext
import testFramework.TestAction
import testFramework.TestCompartment
import testFramework.TestContext
import testFramework.TestSuite
import testFramework.coroutineContext
import testFramework.suite
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// class MyTestSession : TestSession(defaultCompartment = { TestCompartment.Parallel })

// val TestSuite1 by suite(compartment = TestCompartment.Parallel) {
val TestSuite1 by suite {
    val fixtureA = fixture { MyFirstFixture(this) } closeWith { closeSuspending() }

    aroundAll { testActions ->
        log("aroundAll TestSuite1 start")
        withContext(CoroutineName("aroundAll TestSuite1")) {
            testActions()
        }
        log("aroundAll TestSuite1 end")
    }

    test("test1") {
        log(
            "in TestSuite1.test1 [${currentCoroutineContext()[CoroutineName]}], A=${fixtureA()}," +
                " invocation=${InvocationContext.mode()}"
        )
        fail("something wrong in TestSuite1.test1")
    }

    suite("child-suite2") {
        val fixtureB = fixture { MySecondFixture(this) }

        aroundAll { testActions ->
            log("aroundAll TestSuite1.child-suite2 start")
            withContext(CoroutineName("aroundAll TestSuite1.child-suite2")) {
                testActions()
            }
            log("aroundAll TestSuite1.child-suite2 end")
        }

        test("nested1") {
            log(
                "in TestSuite1.child-suite2.nested1 – before delay, A=${fixtureA()}, B=${fixtureB()}," +
                    " invocation=${InvocationContext.mode()}"
            )
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

val TestSuite2 by suite {
    test("test1", configuration = { isEnabled = false }) {
        log("in TestSuite2.test1")
    }

    test("test2 with strange characters <&>'Ä (user-defined function)", timeout = 0.1.seconds) {
        log("in TestSuite2.test2 – before delay")
        delay(0.3.seconds)
        log("in TestSuite2.test2 – after delay")
    }

    test("test3 (disabled)", configuration = { isEnabled = false }) {
        log("in TestSuite2.test1")
    }

    test2("test4 (user-defined function #2)", timeout = 0.1.seconds) {
        log("in TestSuite2.test4 – before delay")
    }
}

val TestSuite3 by suite(name = "TestSuite3XX") {
    val fixtureC = fixture { MyFirstFixture(this) }

    isEnabled = false

    test("test1") {
        log("in TestSuite3.test1, C=${fixtureC()}")
    }

    test("test2") {
        log("in TestSuite3.test2, C=${fixtureC()}")
    }
}

val TestSuite4 by suite {
    test("unhandled exceptions") {
        with(testScope) {
            log("$this starting, currentTime=$currentTime")
            launch {
                throw AssertionError("could this be unhandled?")
                try {
                    log("$this launched job starting, currentTime=$currentTime")
                    delay(0.2.seconds)
                    log("$this launched job finishing, currentTime=$currentTime")
                } catch (throwable: Throwable) {
                    log("$this launched job caught $throwable, currentTime=$currentTime")
                    throw throwable
                }
            }
            log("$this after launch, currentTime=$currentTime")
            delay(0.1.seconds)
            log("$this finishing, currentTime=$currentTime")
        }
    }

    test("background scope") {
        with(testScope) {
            log("$this starting, currentTime=$currentTime")
            backgroundScope.launch {
                try {
                    log("$this launched job starting, currentTime=$currentTime")
                    delay(0.2.seconds)
                    log("$this launched job finishing, currentTime=$currentTime")
                } catch (throwable: Throwable) {
                    log("$this launched job caught $throwable, currentTime=$currentTime")
                    throw throwable
                }
            }
            log("$this after launch, currentTime=$currentTime")
            delay(0.1.seconds)
            log("$this finishing, currentTime=$currentTime")
        }
    }

    test("unconfined dispatcher", configuration = {
        context = TestContext.coroutineContext(UnconfinedTestDispatcher())
    }) {
        with(testScope) {
            val dispatcher = currentCoroutineContext()[CoroutineDispatcher]
            log("$this starting, currentTime=$currentTime, dispatcher=$dispatcher")
            launch {
                try {
                    log("$this launched job starting, currentTime=$currentTime")
                    delay(0.2.seconds)
                    log("$this launched job finishing, currentTime=$currentTime")
                } catch (throwable: Throwable) {
                    log("$this launched job caught $throwable, currentTime=$currentTime")
                    throw throwable
                }
            }
            log("$this after launch, currentTime=$currentTime")
            delay(0.1.seconds)
            log("$this finishing, currentTime=$currentTime")
        }
    }
}

// Using a class as an alternative way of defining a test suite.
class TestSuite5 :
    TestSuite(
        compartment = TestCompartment.UI(UnconfinedTestDispatcher()),
        {
            test("test1") {
                val dispatcher = currentCoroutineContext()[CoroutineDispatcher]
                log("in TestSuite5.test1, currentTime=${testScope.currentTime}, dispatcher=$dispatcher")
            }

            test("test2") {
                delay(0.2.seconds)
                log("in TestSuite5.test2, currentTime=${testScope.currentTime}")
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

    override fun toString(): String = "${this::class.simpleName}(${suite.displayName}, i=$incarnation, s=$state)"

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

    override fun toString(): String = "${this::class.simpleName}(${suite.displayName}, i=$incarnation, s=$state)"

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

// Using a block body
fun TestSuite.test2(name: String, timeout: Duration, action: TestAction) {
    test(name) {
        try {
            withTimeout(timeout) {
                action()
            }
        } catch (timeoutCancellationException: TimeoutCancellationException) {
            throw AssertionError("$timeoutCancellationException", timeoutCancellationException)
        }
    }
}

private fun AbstractTestElement.log(message: String) {
    // println("[${testPlatform.threadDisplayName()}] $elementName: $message")
    println("$elementPath: $message\n")
}

private fun fail(message: String): Unit = throw AssertionError(message)
