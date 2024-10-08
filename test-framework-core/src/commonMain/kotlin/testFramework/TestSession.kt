package testFramework

open class TestSession protected constructor(
    configuration: TestElementConfiguration.() -> Unit = {
        isSequential = true
    },
    defaultCompartment: (() -> Compartment) = { SequentialCompartment }
) : TestSuite(
    parent = null,
    simpleNameOrNull = "${testPlatform.displayName} session",
    configuration = configuration
) {
    val defaultCompartment: Compartment by lazy { defaultCompartment() }

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

    open class Compartment(name: String, configuration: TestElementConfiguration.() -> Unit) :
        TestSuite(parent = global, simpleNameOrNull = "@$name", configuration = configuration)

    companion object {
        // This property is internal for compiler plugin testing only. Consider it private otherwise.
        internal var singleton: TestSession? = null

        val global: TestSession get() =
            singleton ?: throw IllegalStateException(
                "The test framework was not initialized." +
                    " A TestSession must exist before creating a top-level TestSuite." +
                    "\n\tPlease ensure that the test framework's Gradle plugin is configured."
            )

        val SequentialCompartment by lazy { Compartment(name = "Sequential", configuration = { isSequential = true }) }

        val ParallelCompartment by lazy {
            Compartment(name = "Parallel", configuration = { parallelism = testPlatform.parallelism })
        }

        val BenchmarkCompartment by lazy { Compartment(name = "Benchmark", configuration = { isSequential = true }) }
    }
}
