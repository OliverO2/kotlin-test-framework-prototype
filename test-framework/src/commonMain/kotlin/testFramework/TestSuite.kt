package testFramework

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import testFramework.internal.withParallelism

typealias TestSuiteAction = TestSuite.() -> Unit

open class TestSuite internal constructor(
    parent: TestSuite?,
    simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val configurationAction: TestSuiteAction? = null
) : TestScope(parent, simpleNameOrNull, configuration) {
    internal val childScopes: MutableList<TestScope> = mutableListOf()

    init {
        @Suppress("LeakingThis")
        parent?.registerChildScope(this)
    }

    protected constructor(module: TestModule, configurationAction: TestSuiteAction) :
        this(parent = module, configurationAction = configurationAction)

    protected constructor(configurationAction: TestSuiteAction) :
        this(parent = TestModule.default, configurationAction = configurationAction)

    internal fun registerChildScope(childScope: TestScope) {
        childScopes.add(childScope)
    }

    fun beforeFirstScope(action: TestSuiteAction) {
        effectiveConfiguration.beforeFirstScopeAction = action
    }

    fun beforeEachScope(action: TestSuiteAction) {
        effectiveConfiguration.beforeEachScopeAction = action
    }

    fun aroundEachScope(action: TestScopeWrappingAction) {
        effectiveConfiguration.aroundEachScopeAction = action
    }

    fun afterEachScope(action: TestSuiteAction) {
        effectiveConfiguration.afterEachScopeAction = action
    }

    fun afterLastScope(action: TestSuiteAction) {
        effectiveConfiguration.afterLastScopeAction = action
    }

    fun suite(name: String, configurationAction: TestSuiteAction) {
        TestSuite(this, name, configurationAction = configurationAction)
    }

    fun test(name: String, configuration: TestScopeConfiguration.() -> Unit = {}, action: TestAction) {
        Test(this, name, configuration = configuration, action)
    }

    override fun configure() {
        super.configure()

        configurationAction?.invoke(this)

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

    override suspend fun execute(listener: TestScopeEventListener?) {
        if (!scopeIsEnabled) {
            trackSkipping(listener)
            // This is opinionated, following JUnit Platform TestEngine guidelines in
            // https://junit.org/junit5/docs/current/user-guide/#test-engines-custom:
            // > If a node is reported as skipped, there must not be any events reported for its descendants.
            return
        }

        withExecutionTracking(listener) {
            withParallelism(effectiveConfiguration.parallelism) {
                effectiveConfiguration.beforeFirstScopeAction?.invoke(this)

                val childScopeWrappingActions = childScopeWrappingActions()

                coroutineScope {
                    for (childScope in childScopes) {
                        // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters
                        val wrappedAction =
                            childScopeWrappingActions.fold<TestScopeWrappingAction, TestScopeAction>(
                                { childScope.execute(listener) }
                            ) { innerAction, wrappingAction ->
                                {
                                    wrappingAction(innerAction)
                                }
                            }

                        if (effectiveConfiguration.isSequential == true || parent == null) {
                            wrappedAction()
                        } else {
                            launch {
                                wrappedAction()
                            }
                        }
                    }
                }

                effectiveConfiguration.afterLastScopeAction?.invoke(this)
            }
        }
    }

    /** Returns actions wrapping around a child scope's execution, innermost first. */
    private fun childScopeWrappingActions(): List<TestScopeWrappingAction> {
        // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters

        fun beforeAfterWrappingAction(): TestScopeWrappingAction? {
            if (effectiveConfiguration.beforeEachScopeAction == null &&
                effectiveConfiguration.afterEachScopeAction == null
            ) {
                return null
            }

            return { innerAction: TestScopeAction ->
                effectiveConfiguration.beforeEachScopeAction?.invoke(this)
                innerAction()
                effectiveConfiguration.afterEachScopeAction?.invoke(this)
            }
        }

        // Return a list of wrapping actions from innermost to outermost.
        return listOfNotNull(
            effectiveConfiguration.aroundEachScopeAction,
            beforeAfterWrappingAction()
        )
    }
}
