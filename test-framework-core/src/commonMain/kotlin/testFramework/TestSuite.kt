package testFramework

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.internal.TestReport
import testFramework.internal.TestSession
import testFramework.internal.withParallelism

typealias TestSuiteComponentsDefinition = TestSuite.() -> Unit
typealias TestSuiteAction = suspend TestSuite.() -> Unit
typealias TestSuiteWrappingAction = suspend (suiteAction: TestSuiteAction) -> Unit

open class TestSuite internal constructor(
    parent: TestSuite?,
    simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val componentsDefinition: TestSuiteComponentsDefinition? = null
) : TestScope(parent, simpleNameOrNull, configuration) {

    internal val childScopes: MutableList<TestScope> = mutableListOf()

    private var aroundAllAction: TestSuiteWrappingAction? = null

    /** Fixtures created while executing this suite, in reverse order of fixture creation. */
    private val fixtures = mutableListOf<Fixture<*>>()

    protected constructor(componentsDefinition: TestSuiteComponentsDefinition) :
        this(parent = TestSession, componentsDefinition = componentsDefinition)

    internal fun registerChildScope(childScope: TestScope) {
        childScopes.add(childScope)
    }

    fun aroundAll(action: TestSuiteWrappingAction) {
        aroundAllAction = action
    }

    fun suite(name: String, componentsDefinition: TestSuiteComponentsDefinition) {
        TestSuite(this, name, componentsDefinition = componentsDefinition)
    }

    fun test(name: String, configuration: TestScopeConfiguration.() -> Unit = {}, action: TestAction) {
        Test(this, name, configuration = configuration, action)
    }

    override fun configure() {
        super.configure()

        componentsDefinition?.invoke(this)

        childScopes.forEach {
            it.configure()
        }

        if (scopeIsEnabled && childScopes.isNotEmpty()) {
            if (childScopes.any { it.scopeIsFocused }) {
                // Disable all non-focused child scopes (disabling does not propagate to transitive child scopes).
                childScopes.forEach {
                    it.scopeIsEnabled = it.scopeIsFocused
                }
            } else {
                // Disable this scope if none of its child scopes are enabled.
                if (!childScopes.any { it.scopeIsEnabled }) {
                    scopeIsEnabled = false
                }
            }
        }
    }

    override suspend fun execute(report: TestReport) {
        executeReporting(report) {
            if (scopeIsEnabled) {
                val childScopeActions = allChildScopesWrappingActions().wrappedAround {
                    coroutineScope {
                        for (childScope in childScopes) {
                            if (effectiveConfiguration.isSequential == true) {
                                childScope.execute(report)
                            } else {
                                launch {
                                    childScope.execute(report)
                                }
                            }
                        }
                    }
                }

                withParallelism(effectiveConfiguration.parallelism) {
                    childScopeActions()
                }
            } else {
                // "Execute" child scopes for reporting only.
                for (childScope in childScopes) {
                    childScope.execute(report)
                }
            }
        }
    }

    private fun List<TestSuiteWrappingAction>.wrappedAround(innermostAction: TestSuiteAction): TestSuiteAction =
        fold(innermostAction) { innerAction, wrappingAction ->
            { wrappingAction(innerAction) }
        }

    /** Returns actions wrapping around all child scope's execution, innermost first. */
    private fun allChildScopesWrappingActions(): List<TestSuiteWrappingAction> = listOfNotNull(
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
