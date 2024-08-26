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
            beforeFirstScope { log("$scopeName: beforeFirstScope TestScope1") }

            beforeEachScope { invocation -> log("$invocation: beforeEachScope TestScope1") }

            aroundEachScope { invocation, scopeAction ->
                log("$invocation: aroundEachScope TestScope1 start")
                withContext(CoroutineName("aroundEachScope TestScope1")) {
                    scopeAction(invocation)
                }
                log("$invocation: aroundEachScope TestScope1 end")
            }

            afterEachScope { invocation -> log("$invocation: afterEachScope TestScope1") }

            afterLastScope { log("$scopeName: afterLastScope TestScope1") }

            test("test1") { invocation ->
                log("$invocation: in TestScope1.test1 [${currentCoroutineContext()[CoroutineName]}]")
                throw AssertionError("something wrong in TestScope1.test1")
            }

            scope("test2") {
                beforeEachScope { subInvocation -> log("$subInvocation: beforeEachScope TestScope1.test2") }

                test("nested1") { subInvocation ->
                    log("$subInvocation: in TestScope1.test2.nested1 – before delay")
                    delay(3.seconds)
                    log("$subInvocation: in TestScope1.test2.nested1 – after delay")
                }

                test("nested2") { subInvocation ->
                    log("$subInvocation: in TestScope1.test2.nested2")
                    throw AssertionError("something wrong in TestScope1.test2.nested2")
                }
            }

            for (generationIndex in 1..3) {
                test("test3.$generationIndex") { invocation ->
                    log("$invocation: in TestScope1.test3.$generationIndex – before delay")
                    delay(2.seconds)
                    log("$invocation: in TestScope1.test3.$generationIndex – after delay")
                }
            }
        }
    )

class TestScope2 :
    TestScope(
        {
            beforeEachScope { log("$scopeName: beforeEachScope TestScope2") }

            afterLastScope { log("$scopeName: afterLastScope TestScope2") }

            test("!test1") { invocation ->
                log("$invocation: in TestScope2.test1")
            }

            test("test2") { invocation ->
                log("$invocation: in TestScope2.test2 – before delay")
                delay(1.seconds)
                log("$invocation: in TestScope2.test2 – after delay")
            }
        }
    )

class TestScope3 :
    TestScope(
        module = TestModule.singleThreaded,
        {
            beforeEachScope { log("$scopeName: beforeEachScope TestScope3") }

            afterLastScope { log("$scopeName: afterLastScope TestScope3") }

            test("!test1") { invocation ->
                log("$invocation: in TestScope3.test1")
            }

            test("test2") { invocation ->
                log("$invocation: in TestScope3.test2 – before delay")
                delay(2.seconds)
                log("$invocation: in TestScope3.test2 – after delay")
            }

            test("test3") { invocation ->
                log("$invocation: in TestScope3.test3 – before delay")
                delay(2.seconds)
                log("$invocation: in TestScope3.test3 – after delay")
            }
        }
    )

expect suspend fun log(message: String)
