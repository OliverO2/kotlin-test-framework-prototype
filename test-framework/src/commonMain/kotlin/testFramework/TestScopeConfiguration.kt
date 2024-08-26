package testFramework

typealias TestScopeInvocationAction = suspend (TestScope.Invocation) -> Unit

typealias TestScopeWrappingAction = suspend (TestScope.Invocation, TestScopeInvocationAction) -> Unit

class TestScopeConfiguration(
    var isEnabled: Boolean = true,
    var isFocused: Boolean = false,
    var parallelism: Int? = null,
    var beforeFirstScopeAction: TestScopeInvocationAction? = null,
    var beforeEachScopeAction: TestScopeInvocationAction? = null,
    var aroundEachScopeAction: TestScopeWrappingAction? = null,
    var afterEachScopeAction: TestScopeInvocationAction? = null,
    var afterLastScopeAction: TestScopeInvocationAction? = null
)
