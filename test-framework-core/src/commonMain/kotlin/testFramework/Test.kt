package testFramework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import testFramework.internal.TestReport
import testFramework.internal.runTestAwaitingCompletion

class Test internal constructor(
    parentSuite: TestSuite,
    elementName: String,
    configuration: Configuration.() -> Unit = {},
    private val action: TestAction
) : TestElement(parentSuite, elementName = elementName, configuration = configuration),
    AbstractTest {

    override fun configure(selection: Selection) {
        super.configure(selection)

        if (isEnabled && !selection.includes(this)) {
            effectiveConfiguration.isEnabled = false
        }
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (isEnabled) {
                effectiveConfiguration.context.executeWithin {
                    val testScopeContext = TestScopeContext.current()

                    if (testScopeContext != null) {
                        executeInTestScope(testScopeContext)
                    } else {
                        TestCoroutineScope(this, CoroutineScope(currentCoroutineContext()), null).action()
                    }
                }
            }
        }
    }

    private suspend fun Test.executeInTestScope(testScopeContext: TestScopeContext) {
        var inheritableContext = currentCoroutineContext().minusKey(Job)
        if (inheritableContext[CoroutineDispatcher] !is TestDispatcher) {
            inheritableContext = inheritableContext.minusKey(CoroutineDispatcher)
        }
        TestScope(inheritableContext)
            .runTestAwaitingCompletion(timeout = testScopeContext.timeout) TestScope@{
                TestCoroutineScope(
                    this@Test,
                    CoroutineScope(currentCoroutineContext()),
                    this@TestScope
                ).action()
            }
    }
}

class TestCoroutineScope(private val test: Test, scope: CoroutineScope, private val testScopeOrNull: TestScope?) :
    AbstractTest by test,
    CoroutineScope by scope {

    val testScope: TestScope get() = testScopeOrNull
        ?: throw IllegalArgumentException("$test is not executing in a TestScope.")
}

typealias TestAction = suspend TestCoroutineScope.() -> Unit
