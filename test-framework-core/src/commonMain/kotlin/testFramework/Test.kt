package testFramework

import testFramework.internal.TestEventTrack
import testFramework.internal.withParallelism

class Test internal constructor(
    parent: TestSuite,
    simpleName: String,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val action: TestAction
) : TestScope(parent, simpleName, configuration = configuration) {

    override suspend fun execute(track: TestEventTrack) {
        executeTracking(track) {
            if (scopeIsEnabled) {
                withParallelism(effectiveConfiguration.parallelism) {
                    action()
                }
            }
        }
    }
}

typealias TestAction = suspend Test.() -> Unit
