package testFramework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import testFramework.internal.TestReport
import kotlin.coroutines.CoroutineContext

class Test internal constructor(
    parent: TestSuite,
    simpleName: String,
    configuration: TestElementConfiguration.() -> Unit = {},
    private val action: TestAction
) : TestElement(parent, simpleName, configuration = configuration) {

    private var _testScope: TestScope? = null
    val testScope: TestScope get() = _testScope
        ?: throw IllegalArgumentException("$this is not executing in a TestScope.")

    private var testCoroutineContext: CoroutineContext? = null
    val testCoroutineScope: CoroutineScope get() = CoroutineScope(
        testCoroutineContext ?: throw IllegalArgumentException("$this is not executing in a coroutine.")
    )

    override fun configure(selection: Selection) {
        super.configure(selection)

        if (isEnabled && !selection.includes(this)) {
            effectiveConfiguration.isEnabled = false
        }
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (isEnabled) {
                require(testCoroutineContext == null) { "$this must not execute concurrently with itself" }
                try {
                    effectiveConfiguration.testConcurrency!!.runInContext { testScope ->
                        _testScope = testScope
                        testCoroutineContext = currentCoroutineContext()
                        action()
                    }
                } finally {
                    _testScope = null
                    testCoroutineContext = null
                }
            }
        }
    }
}

typealias TestAction = suspend Test.() -> Unit
