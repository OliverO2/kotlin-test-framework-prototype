import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import testFramework.Module
import testFramework.Spec
import testFramework.SpecGroup
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

suspend fun runTests(module: Module) {
    module.run {
        registerSpecs(Spec1(), Spec2(), Spec3()) // <- A compiler plugin could generate this.
        configuration {
            specParallelism = 2
        }
        execute()
    }
}

class Spec1 :
    Spec(
        {
            beforeEachTest { println("$it: beforeEachTest") }

            aroundEachTest { test, invocation, testAction ->
                withContext(CoroutineName("foo")) {
                    testAction(test, invocation)
                }
            }

            afterLastTest { println("$testScopeName: afterLastTest") }

            test("test1") {
                println("$it [${coroutineContext[CoroutineName]}]")
                // throw AssertionError("something wrong")
            }

            test("test2", configuration = { invocationCount = 2 }) {
                println("$it [${coroutineContext[CoroutineName]}]")

                beforeEachTest { println("$it: beforeEachTest [nested]") }

                test("nested") {
                    println("$it [${coroutineContext[CoroutineName]}]")
                }
            }

            for (generationIndex in 1..3) {
                test("test3.$generationIndex") {
                    println("$it [${coroutineContext[CoroutineName]}]")
                }
            }
        }
    )

class Spec2 :
    Spec(
        {
            beforeEachTest { println("$testScopeName: beforeEachTest") }

            afterLastTest { println("$testScopeName: afterLastTest") }

            test("!test1") {
                println("$it")
            }

            test("test2", configuration = { timeout = 2.seconds }) {
                println("$it – before delay")
                delay(1.seconds)
                println("$it – after delay")
            }
        }
    )

class Spec3 :
    Spec(
        group = SpecGroup.singleThreaded,
        {
            beforeEachTest { println("$testScopeName: beforeEachTest") }

            afterLastTest { println("$testScopeName: afterLastTest") }

            test("!test1") {
                println("$it")
            }

            test("test2") {
                println("$it – before delay")
                delay(1)
                println("$it – after delay")
            }
        }
    )
