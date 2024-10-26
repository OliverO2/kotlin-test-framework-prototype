package testFramework

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.internal.TestReport

typealias TestSuiteContent = TestSuite.() -> Unit
typealias TestSuiteExecutionAction = suspend TestSuite.() -> Unit
typealias TestSuiteExecutionWrappingAction = suspend (suiteAction: TestSuiteExecutionAction) -> Unit

open class TestSuite internal constructor(
    parentSuite: TestSuite?,
    simpleNameOrNull: String? = null,
    configuration: TestElementConfiguration.() -> Unit = {},
    private val content: TestSuiteContent? = null
) : TestElement(parentSuite, simpleNameOrNull, configuration),
    AbstractTestSuite {

    override val childElements: MutableList<TestElement> = mutableListOf()

    override var isEnabled by effectiveConfiguration::isEnabled

    private var innerContext: TestContext = TestContext.fixtureLifecycleAction()

    /** Fixtures created while executing this suite, in reverse order of fixture creation. */
    private val fixtures = mutableListOf<Fixture<*>>()

    protected constructor(content: TestSuiteContent) : this(
        parentSuite = TestSession.global.defaultCompartment,
        content = content
    )

    protected constructor(
        configuration: Configuration.() -> Unit,
        content: TestSuiteContent
    ) : this(
        parentSuite = TestSession.global.defaultCompartment,
        configuration = configuration,
        content = content
    )

    protected constructor(
        compartment: TestCompartment,
        content: TestSuiteContent
    ) : this(parentSuite = compartment, content = content)

    protected constructor(
        compartment: TestCompartment,
        configuration: Configuration.() -> Unit,
        content: TestSuiteContent
    ) : this(parentSuite = compartment, configuration = configuration, content = content)

    internal fun registerChildElement(childElement: TestElement) {
        childElements.add(childElement)
    }

    fun aroundAll(action: TestSuiteExecutionWrappingAction) {
        innerContext = innerContext wrapping { innerAction ->
            val innerActionConverted: TestSuiteExecutionAction = { innerAction() }
            action(innerActionConverted)
        }
    }

    fun suite(name: String, content: TestSuiteContent) {
        TestSuite(this, name, content = content)
    }

    fun test(name: String, configuration: TestElementConfiguration.() -> Unit = {}, action: TestAction) {
        Test(this, name, configuration = configuration, action)
    }

    override fun configure(selection: Selection) {
        super.configure(selection)

        content?.invoke(this)

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

    fun <Value : Any> fixture(value: TestSuite.() -> Value): Fixture<Value> = Fixture(this, value)

    class Fixture<Value : Any> internal constructor(
        private val suite: TestSuite,
        private val newValue: suspend TestSuite.() -> Value
    ) {
        private var value: Value? = null
        private var close: suspend Value.() -> Unit = { (this as? AutoCloseable)?.close() }

        suspend operator fun invoke(): Value {
            if (value == null) {
                value = suite.newValue()
                suite.fixtures.add(0, this)
            }
            return value!!
        }

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
