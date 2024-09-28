package testFramework

import testFramework.internal.withParallelism

class Test internal constructor(
    parent: TestSuite,
    simpleName: String,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val action: TestAction
) : TestScope(parent, simpleName, configuration = configuration) {

    override suspend fun execute(listener: TestScopeEventListener?) {
        if (!scopeIsEnabled) {
            trackSkipping(listener)
            return
        }

        withExecutionTracking(listener) {
            withParallelism(effectiveConfiguration.parallelism) {
                action()
            }
        }
    }
}

typealias TestAction = suspend Test.() -> Unit
