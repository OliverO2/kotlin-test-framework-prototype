package testFramework

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
                    " A TestSession must exist before creating a top-level TestSuite." +
                    "\n\tPlease ensure that the test framework's Gradle plugin is configured."
            )
    }
}
