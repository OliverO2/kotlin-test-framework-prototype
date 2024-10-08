package testFramework

class TestElementConfiguration {
    var isEnabled: Boolean = true // children inherit a disabled state
    var isFocused: Boolean = false
    var isSequential: Boolean? = null // inheritable
    var parallelism: Int? = null // inheritable (via coroutines dispatcher)
        set(value) {
            if (value != null) {
                isSequential = false
            }
            field = value
        }

    internal fun inheritFrom(parent: TestElementConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            if (isSequential == null) isSequential = parent.isSequential
        }
    }
}
