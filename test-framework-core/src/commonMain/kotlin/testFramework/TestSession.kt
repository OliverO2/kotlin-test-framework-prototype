package testFramework

import kotlinx.coroutines.Dispatchers

/**
 * A compilation module's root test suite, holding the module-wide default configuration.
 *
 * A compilation module may declare at most one test session. It is the root of the test element hierarchy.
 * The test framework's generated code invokes `initializeTestFramework` at module initialization time, making
 * sure that a valid [TestSession] exists before instantiating any top-level [TestSuite].
 *
 * A custom [TestSession] specifying a global configuration can be declared like this:
 * ```
 * class MyTestSession :
 *     TestSession(
 *         configuration = TestConfig.coroutineContext(UnconfinedTestDispatcher()),
 *         defaultCompartment = { TestCompartment.Concurrent }
 *     )
 * ```
 */
@TestDiscoverable
open class TestSession protected constructor(
    configuration: TestConfig = DefaultConfiguration,
    defaultCompartment: (() -> TestCompartment) = { TestCompartment.Default }
) : TestSuite(
    parentSuite = null,
    elementName = "${testPlatform.displayName} session",
    configuration = configuration
),
    AbstractTestSession {

    val defaultCompartment: TestCompartment by lazy { defaultCompartment() }

    init {
        if (singleton != null) {
            throw IllegalArgumentException(
                "The module has been initialized with a TestSession before." +
                    " There must be only one TestSession per compilation module."
            )
        }
        @Suppress("LeakingThis")
        singleton = this
    }

    internal constructor() : this(configuration = DefaultConfiguration)

    companion object {
        /**
         * The default session configuration.
         *
         * Executing elements sequentially on [Dispatchers.Default], using [kotlinx.coroutines.test.TestScope]
         * inside tests.
         */
        val DefaultConfiguration: TestConfig =
            TestConfig.invocation(TestInvocation.SEQUENTIAL)
                .coroutineContext(Dispatchers.Default)
                .testScope(true)

        private var singleton: TestSession? = null

        internal val global: TestSession get() =
            singleton ?: throw IllegalStateException(
                "The test framework was not initialized." +
                    " A TestSession must exist before creating any top-level TestSuite." +
                    "\n\tPlease ensure that the test framework's Gradle plugin is configured."
            )

        /** Resets global state, enabling the execution of multiple test sessions in one process. */
        internal fun resetState() {
            singleton = null
        }
    }
}
