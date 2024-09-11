package testFramework

import testFramework.internal.withParallelism

class Test(
    parent: TestSuite,
    simpleName: String,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val action: TestAction
) : TestScope(parent, simpleName, configuration = configuration) {

    init {
        parent.registerChildScope(this)
    }

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
