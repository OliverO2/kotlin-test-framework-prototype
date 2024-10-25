package testFramework

import kotlinx.coroutines.CoroutineDispatcher

open class TestCompartment(name: String, configuration: TestElementConfiguration.() -> Unit) :
    TestSuite(parentSuite = TestSession.global, simpleNameOrNull = "@$name", configuration = configuration) {

    companion object {
        val Default by lazy {
            TestCompartment(name = "Default", configuration = {})
        }

        @Suppress("FunctionName")
        fun UI(dispatcher: CoroutineDispatcher, configuration: TestElementConfiguration.() -> Unit = {}) =
            TestCompartment(name = "UI", configuration = {
                configuration()
                context = context
                    .invocation(InvocationContext.Mode.SEQUENTIAL)
                    .coroutineContext(dispatcher)
                    .mainDispatcher(dispatcher)
            })

        val Parallel by lazy {
            TestCompartment(name = "Parallel", configuration = {
                context = TestContext.invocation(InvocationContext.Mode.CONCURRENT)
            })
        }
    }
}
