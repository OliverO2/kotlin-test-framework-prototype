package testFramework

import kotlinx.coroutines.Dispatchers

class TestElementConfiguration {
    var isEnabled: Boolean = true // children inherit a disabled state

    var context: TestContext = TestContext

    internal fun inheritFrom(parent: TestElementConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
        }
    }

    companion object {
        val Default: TestElementConfiguration.() -> Unit = {
            context = TestContext.invocation(InvocationContext.Mode.SEQUENTIAL)
                .coroutineContext(Dispatchers.Default)
                .testScope(true)
        }
    }
}
