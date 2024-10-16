package testFramework

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.internal.TestReport

typealias TestSuiteComponentsDefinition = TestSuite.() -> Unit
typealias TestSuiteAction = suspend TestSuite.() -> Unit
typealias TestSuiteWrappingAction = suspend (suiteAction: TestSuiteAction) -> Unit

open class TestSuite internal constructor(
    parent: TestSuite?,
    simpleNameOrNull: String? = null,
    configuration: TestElementConfiguration.() -> Unit = {},
    private val componentsDefinition: TestSuiteComponentsDefinition? = null
) : TestElement(parent, simpleNameOrNull, configuration) {

    internal val childElements: MutableList<TestElement> = mutableListOf()

    override var isEnabled by effectiveConfiguration::isEnabled
    override var isFocused by effectiveConfiguration::isFocused

    private var aroundAllAction: TestSuiteWrappingAction? = null

    /** Fixtures created while executing this suite, in reverse order of fixture creation. */
    private val fixtures = mutableListOf<Fixture<*>>()

    protected constructor(componentsDefinition: TestSuiteComponentsDefinition) : this(
        parent = TestSession.global.defaultCompartment,
        componentsDefinition = componentsDefinition
    )

    protected constructor(
        compartment: TestCompartment,
        componentsDefinition: TestSuiteComponentsDefinition
    ) : this(parent = compartment, componentsDefinition = componentsDefinition)

    internal fun registerChildElement(childElement: TestElement) {
        childElements.add(childElement)
    }

    fun aroundAll(action: TestSuiteWrappingAction) {
        aroundAllAction = action
    }

    fun suite(name: String, componentsDefinition: TestSuiteComponentsDefinition) {
        TestSuite(this, name, componentsDefinition = componentsDefinition)
    }

    fun test(name: String, configuration: TestElementConfiguration.() -> Unit = {}, action: TestAction) {
        Test(this, name, configuration = configuration, action)
    }

    override fun configure(selection: Selection) {
        super.configure(selection)

        componentsDefinition?.invoke(this)

        childElements.forEach {
            it.configure(selection)
        }

        if (isEnabled && childElements.isNotEmpty()) {
            if (childElements.any { it.isFocused }) {
                // Disable all non-focused child elements (disabling does not propagate to transitive child elements).
                childElements.forEach {
                    it.effectiveConfiguration.isEnabled = it.isFocused
                }
            } else {
                // Disable this element if none of its child elements are enabled.
                if (childElements.none { it.isEnabled }) {
                    effectiveConfiguration.isEnabled = false
                }
            }
        }
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (isEnabled) {
                val childElementActions = allChildElementsWrappingActions().wrappedAround {
                    coroutineScope {
                        for (childElement in childElements) {
                            if (effectiveConfiguration.suiteConcurrency is Concurrency.Sequential) {
                                childElement.execute(report)
                            } else {
                                launch {
                                    childElement.execute(report)
                                }
                            }
                        }
                    }
                }

                effectiveConfiguration.suiteConcurrency!!.runInContext {
                    childElementActions()
                }
            } else {
                // "Execute" disabled child elements for reporting only.
                for (childElement in childElements) {
                    childElement.execute(report)
                }
            }
        }
    }

    private fun List<TestSuiteWrappingAction>.wrappedAround(innermostAction: TestSuiteAction): TestSuiteAction =
        fold(innermostAction) { innerAction, wrappingAction ->
            { wrappingAction(innerAction) }
        }

    /** Returns actions wrapping around all child element's execution, innermost first. */
    private fun allChildElementsWrappingActions(): List<TestSuiteWrappingAction> = listOfNotNull(
        aroundAllAction,
        fixtureLifecycleAction()
    )

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

    private fun fixtureLifecycleAction(): TestSuiteWrappingAction = { innerAction: TestSuiteAction ->
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
