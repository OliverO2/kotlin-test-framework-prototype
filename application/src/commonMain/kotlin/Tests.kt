import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import testFramework.TestModule
import testFramework.TestScope
import kotlin.time.Duration.Companion.seconds

suspend fun runTests() {
    TestModule.execute(TestScope1(), TestScope2(), TestScope3()) // <- A compiler plugin could generate this.
}

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

            scope("test1") { invocation ->
                log("$invocation: in TestScope1.test1 [${currentCoroutineContext()[CoroutineName]}]")
                // throw AssertionError("something wrong")
            }

            scope("test2", { invocationCount = 2 }) { invocation ->
                log("$invocation: in TestScope1.test2 [${currentCoroutineContext()[CoroutineName]}]")

                beforeEachScope { subInvocation -> log("$subInvocation: beforeEachScope TestScope1.test2") }

                scope("nested") { subInvocation ->
                    log("$subInvocation: in TestScope1.test2.nested [${currentCoroutineContext()[CoroutineName]}]")
                }
            }

            for (generationIndex in 1..3) {
                scope("test3.$generationIndex") { invocation ->
                    log(
                        "$invocation: in TestScope1.test3.$generationIndex [${currentCoroutineContext()[CoroutineName]}]"
                    )
                }
            }
        }
    )

class TestScope2 :
    TestScope(
        {
            beforeEachScope { log("$scopeName: beforeEachScope TestScope2") }

            afterLastScope { log("$scopeName: afterLastScope TestScope2") }

            scope("!test1") { invocation ->
                log("$invocation: in TestScope2.test1")
            }

            scope("test2", configuration = { timeout = 2.seconds }) { invocation ->
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

            scope("!test1") { invocation ->
                log("$invocation: in TestScope3.test1")
            }

            scope("test2") { invocation ->
                log("$invocation: in TestScope3.test2 – before delay")
                delay(1)
                log("$invocation: in TestScope3.test2 – after delay")
            }
        }
    )

expect suspend fun log(message: String)
