package testFramework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import testFramework.internal.TestReport

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
                effectiveConfiguration.testConcurrency!!.runInContext { testScope ->
                    TestCoroutineScope(this, CoroutineScope(currentCoroutineContext()), testScope).action()
                }
            }
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
