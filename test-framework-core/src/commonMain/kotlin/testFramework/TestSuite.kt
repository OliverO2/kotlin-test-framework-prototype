package testFramework

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.internal.TestReport

typealias TestSuiteExecutionAction = suspend TestSuite.() -> Unit
typealias TestSuiteExecutionWrappingAction = suspend (suiteAction: TestSuiteExecutionAction) -> Unit

/**
 * Declares a test suite with a number of child test elements (tests and/or suites). A suite may not contain test logic.
 *
 * [compartment] is the optional [TestCompartment]
 *
 * Usage:
 * ```
 * val myTestSuite by testSuite(compartment = TestCompartment.Concurrent) {
 *     // test suite content
 * }
 * ```
 */
@TestDiscoverable
fun testSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    compartment: TestCompartment,
    configuration: TestElement.Configuration.() -> Unit = {},
    content: TestSuite.() -> Unit
): Lazy<TestSuite> = lazy {
    TestSuite(
        parentSuite = compartment,
        elementName = name,
        displayName = displayName,
        configuration = configuration,
        content = content
    )
}

/**
 * Declares a test suite with a number of child test elements (tests and/or suites). A suite may not contain test logic.
 *
 * Usage:
 * ```
 * val myTestSuite by testSuite {
 *     // test suite content
 * }
 * ```
 */
@TestDiscoverable
fun testSuite(
    @TestElementName name: String = "",
    @TestDisplayName displayName: String = name,
    configuration: TestElement.Configuration.() -> Unit = {},
    content: TestSuite.() -> Unit
): Lazy<TestSuite> = lazy {
    TestSuite(
        parentSuite = TestSession.global.defaultCompartment,
        elementName = name,
        displayName = displayName,
        configuration = configuration,
        content = content
    )
}

/**
 * A test suite declaring a number of child test elements (tests and/or suites). A suite may not contain test logic.
 */
@TestDiscoverable
open class TestSuite internal constructor(
    parentSuite: TestSuite?,
    elementName: String,
    displayName: String = elementName,
    configuration: Configuration.() -> Unit = {},
    private val content: TestSuite.() -> Unit = {}
) : TestElement(parentSuite, elementName = elementName, displayName = displayName, configuration),
    AbstractTestSuite {

    override val childElements: MutableList<TestElement> = mutableListOf()

    override var isEnabled by effectiveConfiguration::isEnabled

    private var innerContext: TestContext = TestContext.fixtureLifecycleAction()

    /** Fixtures created while executing this suite, in reverse order of fixture creation. */
    private val fixtures = mutableListOf<Fixture<*>>()

    // region – We need these constructor variants for proper overload resolution with default parameters,
    // because Kotlin determines argument positions before default values are filled in.
    // See https://youtrack.jetbrains.com/issue/KT-48521.

    constructor(
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) : this(
        parentSuite = TestSession.global.defaultCompartment,
        elementName = name,
        displayName = displayName,
        content = content
    )

    constructor(
        configuration: Configuration.() -> Unit,
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) :
        this(
            parentSuite = TestSession.global.defaultCompartment,
            elementName = name,
            displayName = displayName,
            configuration = configuration,
            content = content
        )

    constructor(
        compartment: TestCompartment,
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) : this(
        parentSuite = compartment,
        elementName = name,
        displayName = displayName,
        content = content
    )

    constructor(
        compartment: TestCompartment,
        configuration: Configuration.() -> Unit = {},
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) : this(
        parentSuite = compartment,
        elementName = name,
        displayName = displayName,
        configuration = configuration,
        content = content
    )

    constructor(
        name: String,
        compartment: TestCompartment,
        content: TestSuite.() -> Unit,
        @TestDisplayName displayName: String = name
    ) : this(
        parentSuite = compartment,
        elementName = name,
        displayName = displayName,
        content = content
    )

    constructor(
        name: String,
        configuration: Configuration.() -> Unit,
        content: TestSuite.() -> Unit,
        @TestDisplayName displayName: String = name
    ) : this(
        parentSuite = TestSession.global.defaultCompartment,
        elementName = name,
        displayName = displayName,
        configuration = configuration,
        content = content
    )

    constructor(
        name: String,
        compartment: TestCompartment,
        configuration: Configuration.() -> Unit,
        content: TestSuite.() -> Unit,
        @TestDisplayName displayName: String = name
    ) : this(
        parentSuite = compartment,
        elementName = name,
        displayName = displayName,
        configuration = configuration,
        content = content
    )

    // endregion

    internal fun registerChildElement(childElement: TestElement) {
        childElements.add(childElement)
    }

    /**
     * Declares an [action] wrapping the actions of this test suite's child elements.
     *
     * The wrapping action will be invoked only if at least one child elements is enabled.
     *
     * Usage:
     * ```
     *     aroundAll { testActions ->
     *         withContext(CoroutineName("parent coroutine configured by aroundAll")) {
     *             testActions()
     *         }
     *     }
     * ```
     */
    fun aroundAll(action: TestSuiteExecutionWrappingAction) {
        innerContext = innerContext wrapping { innerAction ->
            val innerActionConverted: TestSuiteExecutionAction = { innerAction() }
            action(innerActionConverted)
        }
    }

    /**
     * Declares a test suite with a number of child test elements (tests and/or suites). A suite may not contain test logic.
     */
    @TestDiscoverable
    fun testSuite(@TestElementName name: String, content: TestSuite.() -> Unit) {
        TestSuite(this, elementName = name, displayName = name, content = content)
    }

    /**
     * Declares a test with an [action] containing test logic.
     */
    @TestDiscoverable
    fun test(@TestElementName name: String, configuration: Configuration.() -> Unit = {}, action: TestAction) {
        Test(this, name, configuration = configuration, action)
    }

    override fun configure(selection: Selection) {
        super.configure(selection)

        content()

        childElements.forEach {
            it.configure(selection)
        }

        if (isEnabled && childElements.none { it.isEnabled }) {
            isEnabled = false
        }
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (isEnabled) {
                (effectiveConfiguration.context wrapping innerContext).executeWithin {
                    val invocationMode = InvocationContext.mode()
                    coroutineScope {
                        for (childElement in childElements) {
                            when (invocationMode) {
                                InvocationContext.Mode.SEQUENTIAL -> {
                                    childElement.execute(report)
                                }

                                InvocationContext.Mode.CONCURRENT -> {
                                    launch {
                                        childElement.execute(report)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // "Execute" disabled child elements for reporting only.
                for (childElement in childElements) {
                    childElement.execute(report)
                }
            }
        }
    }

    /**
     * Declares a fixture, a state holder for a lazily initialized [Value] with a lifetime of `this` test suite.
     *
     * Characteristics:
     * - The fixture is lazily initialized on first use by the [value] lambda.
     * - If [Value] is an [AutoCloseable], the fixture will call `close` at the end of its lifetime, otherwise
     *   `closeWith` can declare a specific action to be called on close.
     * - All test elements within its suite share the same fixture value.
     *
     * Usage:
     *
     * Declare a fixture at the suite level like this:
     * ```
     * val repository = fixture { MyRepository(this) } closeWith { disconnect() }
     * ```
     *
     * Use its value in the suite's child elements by invoking the fixture like this:
     * ```
     * repository().getScore(...)
     * ```
     */
    fun <Value : Any> fixture(value: TestSuite.() -> Value): Fixture<Value> = Fixture(this, value)

    /**
     * A fixture is a state holder for a lazily initialized [Value] with a lifetime of the test suite declaring it.
     *
     * If [Value] is an [AutoCloseable], the fixture will call [close()] at the end of its lifetime, otherwise
     * [closeWith] can declare a specific action to be called on close.
     */
    class Fixture<Value : Any> internal constructor(
        private val suite: TestSuite,
        private val newValue: suspend TestSuite.() -> Value
    ) {
        private var value: Value? = null
        private var close: suspend Value.() -> Unit = { (this as? AutoCloseable)?.close() }

        /** Returns the fixture's value, instantiating it on first use. */
        suspend operator fun invoke(): Value {
            if (value == null) {
                value = suite.newValue()
                suite.fixtures.add(0, this)
            }
            return value!!
        }

        /** Declares [action] to be called when this fixture's lifetime ends. */
        infix fun closeWith(action: suspend Value.() -> Unit): Fixture<Value> {
            close = action
            return this
        }

        internal suspend fun close() {
            value?.let {
                value = null
                it.close()
            }
        }
    }

    private fun TestContext.fixtureLifecycleAction(): TestContext = wrapping { innerAction ->
        var actionException: Throwable? = null

        try {
            innerAction()
        } catch (exception: Throwable) {
            actionException = exception
            throw exception
        } finally {
            withContext(NonCancellable) {
                fixtures.forEach {
                    try {
                        it.close()
                    } catch (closeException: Throwable) {
                        if (actionException == null) {
                            throw closeException
                        } else {
                            actionException.addSuppressed(closeException)
                        }
                    }
                }
            }
        }
    }
}
