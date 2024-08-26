package testFramework

import testFramework.internal.integration.IntellijTestLog
import testFramework.internal.platformParallelism
import testFramework.internal.withSingleThreading

open class TestModule private constructor(parent: TestScope?, configuration: TestScopeConfiguration.() -> Unit = {}) :
    TestScope(parent = parent, configuration = configuration) {

    class Root : TestModule(null) {
        override suspend fun execute(outerInvocation: Invocation) {
            withSingleThreading {
                super.execute(outerInvocation)
            }
        }
    }

    class DefaultModule : TestModule(root, configuration = { parallelism = platformParallelism })

    class SingleThreadedModule : TestModule(root)

    suspend fun execute(listener: (Invocation.Event) -> Unit) {
        execute(Invocation(this, listener))
    }

    companion object {
        // Create only those modules at the top of the hierarchy, which are used by actual test scopes.
        internal val root: TestModule by lazy { Root() }

        val default: TestModule by lazy { DefaultModule() }

        val singleThreaded: TestModule by lazy { SingleThreadedModule() }

        suspend fun execute(@Suppress("UNUSED_PARAMETER") vararg scopes: TestScope) {
            // `scopes` is unused because top-level test scopes register themselves with their root scope
            root.configure()
            root.execute(IntellijTestLog::add)
        }
    }
}
