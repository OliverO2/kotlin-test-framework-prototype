package testFramework

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
 *         configuration = {
 *             context = TestContext.coroutineContext(UnconfinedTestDispatcher())
 *         },
 *         defaultCompartment = { TestCompartment.Concurrent }
 *     )
 * ```
 */
@TestDiscoverable
open class TestSession protected constructor(
    configuration: Configuration.() -> Unit = Configuration.Default,
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
                "The test session has been initialized with a TestSession before." +
                    "There must be only one TestSession per compilation module." +
                    "\nIf this occurs during compiler plugin testing, see the FIXME comment there for details."
            )
        }
        @Suppress("LeakingThis")
        singleton = this
    }

    internal constructor() : this(configuration = Configuration.Default)

    internal companion object {
        // This property is internal for compiler plugin testing only. Consider it private otherwise.
        internal var singleton: TestSession? = null

        internal val global: TestSession get() =
            singleton ?: throw IllegalStateException(
                "The test framework was not initialized." +
                    " A TestSession must exist before creating any top-level TestSuite." +
                    "\n\tPlease ensure that the test framework's Gradle plugin is configured."
            )
    }
}
