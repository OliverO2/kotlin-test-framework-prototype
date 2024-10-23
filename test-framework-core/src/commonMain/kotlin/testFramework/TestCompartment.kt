package testFramework

import kotlinx.coroutines.CoroutineDispatcher

open class TestCompartment(name: String, configuration: TestElementConfiguration.() -> Unit) :
    TestSuite(parentSuite = TestSession.global, simpleNameOrNull = "@$name", configuration = configuration) {

    companion object {
        val Default by lazy {
            TestCompartment(name = "Default", configuration = {}) // Use the session configuration
        }

        @Suppress("FunctionName")
        fun UI(dispatcher: CoroutineDispatcher, configuration: TestElementConfiguration.() -> Unit = {}) =
            TestCompartment(name = "UI", configuration = {
                context(
                    ExecutionContext.Invocation(ExecutionContext.Invocation.Mode.SEQUENTIAL),
                    ExecutionContext.CoroutineContext(dispatcher),
                    ExecutionContext.MainDispatcher(dispatcher)
                )
                configuration()
            })

        val Parallel by lazy {
            TestCompartment(name = "Parallel", configuration = {
                context(ExecutionContext.Invocation(ExecutionContext.Invocation.Mode.CONCURRENT))
            })
        }
    }
}
