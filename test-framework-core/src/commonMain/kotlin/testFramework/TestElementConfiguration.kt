package testFramework

class TestElementConfiguration {
    var isEnabled: Boolean = true // children inherit a disabled state
    var isFocused: Boolean = false

    var suiteConcurrency: Concurrency.SuiteConcurrency? = null // inheritable
        set(value) {
            if (value == null) throw IllegalArgumentException("suiteConcurrency may not be set to null")
            field = value
        }

    var testConcurrency: Concurrency.TestConcurrency? = null // inheritable
        set(value) {
            if (value == null) throw IllegalArgumentException("testConcurrency may not be set to null")
            field = value
        }

    internal fun inheritFrom(parent: TestElementConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            if (suiteConcurrency == null) suiteConcurrency = parent.suiteConcurrency
            if (testConcurrency == null) testConcurrency = parent.testConcurrency
        } else {
            if (suiteConcurrency == null) suiteConcurrency = Concurrency.Sequential
            if (testConcurrency == null) testConcurrency = Concurrency.TestScoped()
        }
    }
}
