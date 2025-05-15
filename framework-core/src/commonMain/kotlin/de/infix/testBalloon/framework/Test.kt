package de.infix.testBalloon.framework

import de.infix.testBalloon.framework.internal.runTestAwaitingCompletion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * A test containing a test [action] which raises assertion errors on failure. The [action] may suspend.
 */
class Test internal constructor(
    parent: TestSuite,
    name: String,
    testConfig: TestConfig,
    private val action: TestAction
) : TestElement(parent, name = name, testConfig = testConfig),
    AbstractTest {

    override fun parameterize(selection: Selection) {
        super.parameterize(selection)

        if (testElementIsEnabled && !selection.includes(this)) testElementIsEnabled = false
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (testElementIsEnabled) {
                testConfig.executeWrapped(this) {
                    val testScopeContext = TestScopeContext.current()

                    if (testScopeContext != null) {
                        executeInTestScope(testScopeContext)
                    } else {
                        coroutineScope {
                            TestCoroutineScope(this@Test, this, null).action()
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes the test action in [kotlinx.coroutines.test.TestScope].
     */
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

class TestCoroutineScope internal constructor(
    private val test: Test,
    scope: CoroutineScope,
    private val testScopeOrNull: TestScope?
) : AbstractTest by test,
    CoroutineScope by scope {

    val testScope: TestScope
        get() = testScopeOrNull
            ?: throw IllegalArgumentException("$test is not executing in a TestScope.")
}

/**
 * A test's action: test logic which raises assertion errors on failure. The action may suspend.
 */
typealias TestAction = suspend TestCoroutineScope.() -> Unit
