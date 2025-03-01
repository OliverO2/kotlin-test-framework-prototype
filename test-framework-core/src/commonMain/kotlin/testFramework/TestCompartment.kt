package testFramework

import kotlinx.coroutines.CoroutineDispatcher

/**
 * A compartment isolating a number of tests from those belonging to other compartments.
 *
 * Compartments make sure that tests with special runtime requirements, like UI tests, can execute in isolation.
 */
open class TestCompartment(name: String, configuration: Configuration.() -> Unit) :
    TestSuite(parentSuite = TestSession.global, elementName = "@$name", configuration = configuration) {

    companion object {
        /**
         * A default compartment executing its tests with a [TestElement.Configuration.Default] configuration.
         */
        val Default by lazy {
            TestCompartment(name = "Default", configuration = {})
        }

        /**
         * A compartment executing its tests sequentially and sharing a single main dispatcher across tests.
         */
        @Suppress("FunctionName")
        fun UI(dispatcher: CoroutineDispatcher, configuration: Configuration.() -> Unit = {}) =
            TestCompartment(name = "UI", configuration = {
                configuration()
                context = context
                    .invocation(InvocationContext.Mode.SEQUENTIAL)
                    .coroutineContext(dispatcher)
                    .mainDispatcher(dispatcher)
            })

        /**
         * A compartment executing its tests concurrently.
         */
        val Concurrent by lazy {
            TestCompartment(name = "Concurrent", configuration = {
                context = TestContext.invocation(InvocationContext.Mode.CONCURRENT)
            })
        }
    }
}
