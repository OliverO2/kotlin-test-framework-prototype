package testFramework

import testFramework.internal.platformParallelism

open class TestModule private constructor(parent: TestScope?, configuration: TestScopeConfiguration.() -> Unit) :
    TestScope(parent = parent, configuration = configuration) {

    class Default : TestModule(root, configuration = { parallelism = platformParallelism })
    class SingleThreaded : TestModule(root, configuration = { parallelism = 1 })

    suspend fun execute() {
        execute(Invocation(this, 0))
    }

    companion object {
        private val root: TestModule = TestModule(null, configuration = {})

        val default: TestModule = Default()
        val singleThreaded: TestModule = SingleThreaded()

        suspend fun execute(vararg scopes: TestScope) {
            root.execute()
        }
    }
}
