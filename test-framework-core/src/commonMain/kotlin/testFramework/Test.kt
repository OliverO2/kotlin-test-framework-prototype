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
    simpleName: String,
    configuration: TestElementConfiguration.() -> Unit = {},
    private val action: TestAction
) : TestElement(parentSuite, simpleName, configuration = configuration),
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
                configurationContextWrappingActions().wrappedAround {
                    val testScopeContext = ExecutionContext.TestScope.contextOrNull()

                    if (testScopeContext != null) {
                        executeInTestScope(testScopeContext)
                    } else {
                        TestCoroutineScope(this, CoroutineScope(currentCoroutineContext()), null).action()
                    }
                }.invoke()
            }
        }
    }

    private suspend fun Test.executeInTestScope(testScopeContext: ExecutionContext.TestScope.Context) {
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

    private fun List<ExecutionWrappingAction>.wrappedAround(innermostAction: suspend () -> Unit): suspend () -> Unit =
        fold(innermostAction) { innerAction, wrappingAction ->
            { wrappingAction(innerAction) }
        }

    /** Returns actions wrapping the configuration contexts (innermost context first) around an inner action. */
    private fun configurationContextWrappingActions() = effectiveConfiguration.contexts.map { it.wrappingAction }
}

class TestCoroutineScope(private val test: Test, scope: CoroutineScope, private val testScopeOrNull: TestScope?) :
    AbstractTest by test,
    CoroutineScope by scope {

    val testScope: TestScope get() = testScopeOrNull
        ?: throw IllegalArgumentException("$test is not executing in a TestScope.")
}

typealias TestAction = suspend TestCoroutineScope.() -> Unit
