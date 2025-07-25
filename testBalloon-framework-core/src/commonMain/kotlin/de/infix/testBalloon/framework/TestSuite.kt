package de.infix.testBalloon.framework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
    compartment: () -> TestCompartment,
    testConfig: TestConfig = TestConfig,
    content: TestSuite.() -> Unit
): Lazy<TestSuite> = lazy {
    TestSuite(
        parent = compartment(),
        name = name,
        displayName = displayName,
        testConfig = testConfig,
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
    testConfig: TestConfig = TestConfig,
    content: TestSuite.() -> Unit
): Lazy<TestSuite> = lazy {
    TestSuite(
        parent = TestSession.global.defaultCompartment,
        name = name,
        displayName = displayName,
        testConfig = testConfig,
        content = content
    )
}

/**
 * A test suite declaring a number of child test elements (tests and/or suites). A suite may not contain test logic.
 */
@TestDiscoverable
open class TestSuite internal constructor(
    parent: TestSuite?,
    name: String,
    displayName: String = name,
    testConfig: TestConfig = TestConfig,
    private val content: TestSuite.() -> Unit = {}
) : TestElement(parent, name = name, displayName = displayName, testConfig),
    AbstractTestSuite {

    override val testElementChildren: Iterable<TestElement> by ::children

    private val children: MutableList<TestElement> = mutableListOf()

    private val childNameCount: MutableMap<String, Int> = mutableMapOf()

    /**
     * The test suite's [CoroutineScope], valid only during the suite's execution.
     *
     * It can be used to launch coroutines in test fixtures. Such coroutines should complete or be cancelled
     * explicitly when their fixture closes.
     */
    val testSuiteScope: CoroutineScope
        get() = executionContext?.let { CoroutineScope(it) }
            ?: throw IllegalStateException("$testElementPath: testSuiteScope is only available during execution")

    /** The [CoroutineContext] used by this suite during execution. */
    private var executionContext: CoroutineContext? = null

    private var privateConfiguration: TestConfig = TestConfig.suiteLifecycleAction()

    /** Fixtures created while executing this suite, in reverse order of fixture creation. */
    private val fixtures = mutableListOf<Fixture<*>>()

    // region – We need these constructor variants only for top-level test suites declared as classes.
    //
    // The constructor variants ensure proper overload resolution with default parameters,
    // because Kotlin determines argument positions before default values are filled in.
    // See https://youtrack.jetbrains.com/issue/KT-48521.

    protected constructor(
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) : this(
        parent = TestSession.global.defaultCompartment,
        name = name,
        displayName = displayName,
        content = content
    )

    protected constructor(
        testConfig: TestConfig,
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) :
        this(
            parent = TestSession.global.defaultCompartment,
            name = name,
            displayName = displayName,
            testConfig = testConfig,
            content = content
        )

    protected constructor(
        compartment: TestCompartment,
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) : this(
        parent = compartment,
        name = name,
        displayName = displayName,
        content = content
    )

    protected constructor(
        compartment: TestCompartment,
        testConfig: TestConfig,
        content: TestSuite.() -> Unit,
        @TestElementName name: String = "",
        @TestDisplayName displayName: String = name
    ) : this(
        parent = compartment,
        name = name,
        displayName = displayName,
        testConfig = testConfig,
        content = content
    )

    protected constructor(
        name: String,
        testConfig: TestConfig,
        content: TestSuite.() -> Unit,
        @TestDisplayName displayName: String = name
    ) : this(
        parent = TestSession.global.defaultCompartment,
        name = name,
        displayName = displayName,
        testConfig = testConfig,
        content = content
    )

    protected constructor(
        name: String,
        compartment: TestCompartment,
        content: TestSuite.() -> Unit,
        @TestDisplayName displayName: String = name
    ) : this(
        parent = compartment,
        name = name,
        displayName = displayName,
        content = content
    )

    protected constructor(
        name: String,
        compartment: TestCompartment,
        testConfig: TestConfig,
        content: TestSuite.() -> Unit,
        @TestDisplayName displayName: String = name
    ) : this(
        parent = compartment,
        name = name,
        displayName = displayName,
        testConfig = testConfig,
        content = content
    )

    // endregion

    internal fun registerUniqueChildElementName(initialName: String): String {
        val nameCount = (childNameCount[initialName] ?: 0) + 1
        childNameCount[initialName] = nameCount
        return if (nameCount == 1) {
            initialName
        } else {
            "$initialName($nameCount)"
        }
    }

    internal fun registerChildElement(childElement: TestElement) {
        require(
            this == suitesInConfigurationScope.firstOrNull() ||
                ( // TestCompartments and TestSession accept children without being in configuration scope,
                    testElementParent?.testElementParent == null &&
                        // but only if no suite below a compartment is in configuration scope.
                        suitesInConfigurationScope.isEmpty()
                    )
        ) {
            "$childElement tried to register as a child of $this," +
                " which currently is not the closest configuration scope.\n" +
                "\tThe closest configuration scope at this point is ${suitesInConfigurationScope.firstOrNull()}."
        }
        children.add(childElement)
    }

    /**
     * Executes [action] on all child elements, recursively, in depth-first order.
     */
    internal suspend fun forEachChildTreeElement(action: suspend (element: TestElement) -> Unit) {
        for (childElement in testElementChildren) {
            if (childElement is TestSuite) {
                childElement.forEachChildTreeElement(action)
            }
            action(childElement)
        }
    }

    /**
     * Declares an [executionWrappingAction] wrapping the actions of this test suite.
     *
     * The wrapping action will be invoked only if at least one child element is enabled.
     * See also [TestExecutionWrappingAction] for requirements.
     *
     * Usage:
     * ```
     *     aroundAll { testSuiteAction ->
     *         withContext(CoroutineName("parent coroutine configured by aroundAll")) {
     *             testSuiteAction()
     *         }
     *     }
     * ```
     */
    fun aroundAll(executionWrappingAction: TestSuiteExecutionWrappingAction) {
        privateConfiguration = privateConfiguration.aroundAll { elementAction ->
            executionWrappingAction { elementAction() }
        }
    }

    /**
     * Declares a test suite with child test elements (tests and/or suites). A suite may not contain test logic.
     */
    @TestDiscoverable
    fun testSuite(
        @TestElementName name: String,
        @TestDisplayName displayName: String = name,
        testConfig: TestConfig = TestConfig,
        content: TestSuite.() -> Unit
    ) {
        TestSuite(this, name = name, displayName = displayName, testConfig = testConfig, content = content)
    }

    /**
     * Declares a test with an [action] containing test logic.
     */
    @TestDiscoverable
    fun test(@TestElementName name: String, testConfig: TestConfig = TestConfig, action: TestAction) {
        Test(this, name, testConfig = testConfig, action)
    }

    override fun parameterize(selection: Selection, report: TestConfigurationReport) {
        configureReporting(report) {
            inConfigurationScope {
                content()
            }

            super.parameterize(selection, report)

            testElementChildren.forEach {
                it.parameterize(selection, report)
            }

            if (testElementIsEnabled && testElementChildren.none { it.testElementIsEnabled }) {
                testElementIsEnabled = false
            }
        }
    }

    override suspend fun execute(report: TestExecutionReport) {
        executeReporting(report) {
            if (testElementIsEnabled) {
                testConfig.chainedWith(privateConfiguration).executeWrapped(this) {
                    val invocation = if (testElementParent == null) {
                        // A TestSession (no parent) must always execute its compartments sequentially.
                        TestInvocation.SEQUENTIAL
                    } else {
                        TestInvocation.current()
                    }
                    coroutineScope {
                        for (childElement in testElementChildren) {
                            when (invocation) {
                                TestInvocation.SEQUENTIAL -> {
                                    childElement.execute(report)
                                }

                                TestInvocation.CONCURRENT -> {
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
                for (childElement in testElementChildren) {
                    childElement.execute(report)
                }
            }
        }
    }

    companion object {
        /** A stack of suites in configuration scope, innermost scope first */
        private val suitesInConfigurationScope = mutableListOf<TestSuite>()

        /** Executes [action] in the configuration scope of [this] suite. */
        fun TestSuite.inConfigurationScope(action: () -> Unit) {
            suitesInConfigurationScope.add(0, this)
            try {
                action()
            } finally {
                check(suitesInConfigurationScope.removeAt(0) == this)
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
     * val repository = testFixture { MyRepository(this) } closeWith { disconnect() }
     * ```
     *
     * Use its value in the suite's child elements by invoking the fixture like this:
     * ```
     * repository().getScore(...)
     * ```
     */
    fun <Value : Any> testFixture(value: suspend TestSuite.() -> Value): Fixture<Value> = Fixture(this, value)

    /**
     * A fixture is a state holder for a lazily initialized [Value] with a lifetime of the test suite declaring it.
     *
     * If [Value] is an [AutoCloseable], the fixture will call [close] at the end of its lifetime, otherwise
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

    private fun TestConfig.suiteLifecycleAction(): TestConfig = executionWrapping { elementAction ->
        var actionException: Throwable? = null

        this@TestSuite.executionContext = currentCoroutineContext()

        try {
            elementAction()
        } catch (exception: Throwable) {
            actionException = exception
        } finally {
            withContext(NonCancellable) {
                for (fixture in this@TestSuite.fixtures) {
                    try {
                        fixture.close()
                    } catch (closeException: Throwable) {
                        if (actionException == null) {
                            actionException = closeException
                        } else {
                            actionException.addSuppressed(closeException)
                        }
                    }
                }
            }

            this@TestSuite.executionContext = null

            if (actionException != null) {
                throw actionException
            }
        }
    }
}
