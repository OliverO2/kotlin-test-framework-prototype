package testFramework

class TestScopeConfiguration(
    var isEnabled: Boolean = true, // children inherit a disabled state
    var isFocused: Boolean = false,
    var isSequential: Boolean? = null, // inheritable
    var parallelism: Int? = null, // inheritable (via coroutines dispatcher)
    var beforeFirstScopeAction: TestSuiteAction? = null,
    var beforeEachScopeAction: TestSuiteAction? = null,
    var aroundEachScopeAction: TestScopeWrappingAction? = null,
    var afterEachScopeAction: TestSuiteAction? = null,
    var afterLastScopeAction: TestSuiteAction? = null
) {
    fun inheritFrom(parent: TestScopeConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            if (isSequential == null) isSequential = parent.isSequential
        }
    }
}

typealias TestScopeAction = suspend TestScope.() -> Unit
typealias TestScopeWrappingAction = suspend (TestScopeAction) -> Unit
