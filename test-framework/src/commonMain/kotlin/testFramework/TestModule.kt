package testFramework

import testFramework.internal.integration.IntellijTestLog
import testFramework.internal.withSingleThreading

open class TestModule private constructor(parent: TestModule?, configuration: TestScopeConfiguration.() -> Unit = {}) :
    TestSuite<Nothing>(parent = parent, configuration = configuration) {

    class Root : TestModule(parent = null)

    class DefaultModule : TestModule(parent = root, configuration = { parallelism = testPlatform.parallelism })

    class SingleThreadedModule : TestModule(parent = root) {
        override suspend fun execute(listener: TestScopeEventListener?) {
            withSingleThreading {
                super.execute(listener)
            }
        }
    }

    class SequentialModule : TestModule(parent = root, configuration = { isSequential = true })

    companion object {
        // Create only those modules at the top of the hierarchy, which are used by actual test scopes.
        internal val root: TestModule by lazy { Root() }

        val default: TestModule by lazy { DefaultModule() }
        val singleThreaded: TestModule by lazy { SingleThreadedModule() }
        val sequential: TestModule by lazy { SequentialModule() }

        internal suspend fun execute(
            listener: TestScopeEventListener?,
            @Suppress("UNUSED_PARAMETER") vararg scopes: TestScope
        ) {
            // `scopes` is unused because top-level test scopes register themselves with their root scope
            root.configure()
            root.execute(listener)
        }

        suspend fun execute(vararg scopes: TestScope) {
            execute(IntellijTestLog::add, *scopes)
        }
    }
}
