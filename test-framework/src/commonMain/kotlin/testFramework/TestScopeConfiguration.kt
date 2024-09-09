package testFramework

typealias TestScopeInvocationAction = suspend (TestScope.Invocation) -> Unit

typealias TestScopeWrappingAction = suspend (TestScope.Invocation, TestScopeInvocationAction) -> Unit

class TestScopeConfiguration(
    var isEnabled: Boolean = true, // children inherit a disabled state
    var isFocused: Boolean = false,
    var isSequential: Boolean? = null, // inheritable
    var parallelism: Int? = null, // inheritable (via coroutines dispatcher)
    var beforeFirstScopeAction: TestScopeInvocationAction? = null,
    var beforeEachScopeAction: TestScopeInvocationAction? = null,
    var aroundEachScopeAction: TestScopeWrappingAction? = null,
    var afterEachScopeAction: TestScopeInvocationAction? = null,
    var afterLastScopeAction: TestScopeInvocationAction? = null
) {
    fun inheritFrom(parent: TestScopeConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            if (isSequential == null) isSequential = parent.isSequential
        }
    }
}
