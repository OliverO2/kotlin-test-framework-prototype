package testFramework

import testFramework.internal.withParallelism

class Test<Fixture : Any> internal constructor(
    parent: TestSuite<Fixture>,
    simpleName: String,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val action: TestAction<Fixture>
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

typealias TestAction<Fixture> = suspend Test<Fixture>.() -> Unit
