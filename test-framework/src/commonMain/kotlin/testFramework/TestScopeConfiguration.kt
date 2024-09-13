package testFramework

class TestScopeConfiguration(
    var isEnabled: Boolean = true, // children inherit a disabled state
    var isFocused: Boolean = false,
    var isSequential: Boolean? = null, // inheritable
    var parallelism: Int? = null // inheritable (via coroutines dispatcher)
) {
    fun inheritFrom(parent: TestScopeConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            if (isSequential == null) isSequential = parent.isSequential
        }
    }
}
