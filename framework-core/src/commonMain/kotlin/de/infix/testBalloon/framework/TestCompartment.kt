package de.infix.testBalloon.framework

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * A compartment isolating a number of tests from those belonging to other compartments.
 *
 * Compartments make sure that tests with special runtime requirements, like UI tests, can execute in isolation.
 */
open class TestCompartment(name: String, testConfig: TestConfig) :
    TestSuite(parent = TestSession.global, name = "@$name", testConfig = testConfig) {

    companion object {
        /**
         * The default compartment.
         */
        val Default get() = default ?: TestCompartment(name = "Default", testConfig = TestConfig).also { default = it }

        private var default: TestCompartment? = null

        /**
         * A compartment executing its tests concurrently.
         */
        val Concurrent get() =
            concurrent
                ?: TestCompartment(
                    name = "Concurrent",
                    testConfig = TestConfig.invocation(TestInvocation.CONCURRENT)
                ).also { concurrent = it }

        private var concurrent: TestCompartment? = null

        /**
         * A compartment executing its tests sequentially and establishing a Main dispatcher.
         *
         * If [mainDispatcher] is `null`, a single-threaded dispatcher is used.
         * [testConfig] is chained to the compartment's default configuration, and thus can override it.
         */
        @Suppress("FunctionName")
        @OptIn(ExperimentalCoroutinesApi::class)
        fun UI(mainDispatcher: CoroutineDispatcher? = null, testConfig: TestConfig = TestConfig): TestCompartment =
            TestCompartment(
                name = "UI",
                testConfig = TestConfig
                    .invocation(TestInvocation.SEQUENTIAL)
                    .mainDispatcher(mainDispatcher)
                    .chainedWith(testConfig)
            )

        /** Resets global state, enabling the execution of multiple test sessions in one process. */
        internal fun resetState() {
            default = null
            concurrent = null
        }
    }
}
