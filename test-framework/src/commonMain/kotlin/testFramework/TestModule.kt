package testFramework

import testFramework.internal.integration.IntellijTestLog
import testFramework.internal.withSingleThreading

open class TestModule private constructor(parent: TestScope?, configuration: TestScopeConfiguration.() -> Unit = {}) :
    TestScope(parent = parent, configuration = configuration) {

    class Root : TestModule(parent = null)

    class DefaultModule : TestModule(parent = root, configuration = { parallelism = testPlatform.parallelism })

    class SingleThreadedModule : TestModule(parent = root) {
        override suspend fun execute(outerInvocation: Invocation) {
            withSingleThreading {
                super.execute(outerInvocation)
            }
        }
    }

    class SequentialModule : TestModule(parent = root, configuration = { isSequential = true })

    suspend fun execute(listener: (Invocation.Event) -> Unit) {
        execute(Invocation(this, listener))
    }

    companion object {
        // Create only those modules at the top of the hierarchy, which are used by actual test scopes.
        internal val root: TestModule by lazy { Root() }

        val default: TestModule by lazy { DefaultModule() }
        val singleThreaded: TestModule by lazy { SingleThreadedModule() }
        val sequential: TestModule by lazy { SequentialModule() }

        suspend fun execute(@Suppress("UNUSED_PARAMETER") vararg scopes: TestScope) {
            // `scopes` is unused because top-level test scopes register themselves with their root scope
            root.configure()
            root.execute(IntellijTestLog::add)
        }
    }
}
