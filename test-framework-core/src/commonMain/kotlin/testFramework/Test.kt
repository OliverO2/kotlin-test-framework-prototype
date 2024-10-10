package testFramework

import testFramework.internal.TestReport
import testFramework.internal.withParallelism

class Test internal constructor(
    parent: TestSuite,
    simpleName: String,
    configuration: TestElementConfiguration.() -> Unit = {},
    private val action: TestAction
) : TestElement(parent, simpleName, configuration = configuration) {

    override fun configure(selection: Selection) {
        super.configure(selection)

        if (isEnabled && !selection.includes(this)) {
            isEnabled = false
        }
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (isEnabled) {
                withParallelism(effectiveConfiguration.parallelism) {
                    action()
                }
            }
        }
    }
}

typealias TestAction = suspend Test.() -> Unit
