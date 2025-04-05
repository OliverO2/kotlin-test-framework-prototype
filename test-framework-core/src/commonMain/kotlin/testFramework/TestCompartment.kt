package testFramework

import kotlinx.coroutines.CoroutineDispatcher

/**
 * A compartment isolating a number of tests from those belonging to other compartments.
 *
 * Compartments make sure that tests with special runtime requirements, like UI tests, can execute in isolation.
 */
open class TestCompartment(name: String, configuration: TestConfig) :
    TestSuite(parentSuite = TestSession.global, elementName = "@$name", configuration = configuration) {

    companion object {
        /**
         * The default compartment.
         */
        val Default get() = default
            ?: TestCompartment(name = "Default", configuration = TestConfig).also { default = it }

        private var default: TestCompartment? = null

        /**
         * A compartment executing its tests concurrently.
         */
        val Concurrent get() =
            concurrent
                ?: TestCompartment(
                    name = "Concurrent",
                    configuration = TestConfig.invocation(TestInvocation.CONCURRENT)
                ).also { concurrent = it }

        private var concurrent: TestCompartment? = null

        /**
         * A compartment executing its tests sequentially and sharing a single main dispatcher across tests.
         */
        @Suppress("FunctionName")
        fun UI(dispatcher: CoroutineDispatcher, configuration: TestConfig = TestConfig) = TestCompartment(
            name = "UI",
            configuration = configuration
                .invocation(TestInvocation.SEQUENTIAL)
                .coroutineContext(dispatcher)
                .mainDispatcher(dispatcher)
        )

        /** Resets global state, enabling the execution of multiple test sessions in one process. */
        internal fun resetState() {
            default = null
            concurrent = null
        }
    }
}
