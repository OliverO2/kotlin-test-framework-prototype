package testFramework

import testFramework.internal.withParallelism

class Test(
    parent: TestScope,
    simpleName: String,
    configuration: TestScopeConfiguration.() -> Unit = {
    },
    private val invocationAction: TestScopeInvocationAction
) : TestScope(parent, simpleName, configuration) {
    override suspend fun execute(outerInvocation: Invocation) {
        val invocation = Invocation(this, outerInvocation.listener)

        if (!scopeIsEnabled) {
            invocation.trackSkipping()
            return
        }

        invocation.withExecutionTracking {
            withParallelism(effectiveConfiguration.parallelism) {
                invocationAction.invoke(invocation)
            }
        }
    }
}
