package testFramework

import kotlin.time.Duration

typealias TestScopeInvocationAction = suspend (TestScope.Invocation) -> Unit

typealias TestScopeWrappingAction = suspend (TestScope.Invocation, TestScopeInvocationAction) -> Unit

data class TestScopeConfiguration(
    var isEnabled: Boolean = true,
    var invocationCount: Int = 1,
    var timeout: Duration? = null,
    var parallelism: Int? = null,
    var beforeFirstScopeAction: TestScopeInvocationAction? = null,
    var beforeEachScopeAction: TestScopeInvocationAction? = null,
    var aroundEachScopeAction: TestScopeWrappingAction? = null,
    var afterEachScopeAction: TestScopeInvocationAction? = null,
    var afterLastScopeAction: TestScopeInvocationAction? = null
) {
    internal val subScopes: MutableList<TestScope> = mutableListOf()
}
