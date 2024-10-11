package testFramework

open class TestSession protected constructor(
    configuration: TestElementConfiguration.() -> Unit = {
        isSequential = true
    },
    defaultCompartment: (() -> TestCompartment) = { TestCompartment.Sequential }
) : TestSuite(
    parent = null,
    simpleNameOrNull = "${testPlatform.displayName} session",
    configuration = configuration
) {
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

    internal constructor() : this(configuration = { isSequential = true })

    companion object {
        // This property is internal for compiler plugin testing only. Consider it private otherwise.
        internal var singleton: TestSession? = null

        val global: TestSession get() =
            singleton ?: throw IllegalStateException(
                "The test framework was not initialized." +
                    " A TestSession must exist before creating a top-level TestSuite." +
                    "\n\tPlease ensure that the test framework's Gradle plugin is configured."
            )
    }
}
