package testFramework

import kotlinx.coroutines.Dispatchers

class TestElementConfiguration {
    var isEnabled: Boolean = true // children inherit a disabled state

    internal var contexts: List<ExecutionContext> = emptyList()

    fun context(vararg contexts: ExecutionContext) {
        this.contexts = contexts.reversed()
    }

    internal fun inheritFrom(parent: TestElementConfiguration?) {
        if (parent != null) {
            if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
        }
    }

    companion object {
        val Default: TestElementConfiguration.() -> Unit = {
            context(
                ExecutionContext.Invocation(ExecutionContext.Invocation.Mode.SEQUENTIAL),
                ExecutionContext.CoroutineContext(Dispatchers.Default),
                ExecutionContext.TestScope(true)
            )
        }
    }
}
