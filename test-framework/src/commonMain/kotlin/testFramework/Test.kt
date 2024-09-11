package testFramework

import testFramework.internal.withParallelism

class Test(
    parent: TestScope,
    simpleName: String,
    configuration: TestScopeConfiguration.() -> Unit = {
    },
    private val invocationAction: TestScopeInvocationAction
) : TestScope(parent, simpleName, configuration = configuration) {
    override suspend fun execute(listener: TestScopeEventListener?) {
        if (!scopeIsEnabled) {
            trackSkipping(listener)
            return
        }

        withExecutionTracking(listener) {
            withParallelism(effectiveConfiguration.parallelism) {
                invocationAction()
            }
        }
    }
}
