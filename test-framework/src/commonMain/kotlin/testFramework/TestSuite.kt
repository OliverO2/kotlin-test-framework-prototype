package testFramework

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import testFramework.internal.withParallelism

typealias TestSuiteAction = TestSuite.() -> Unit
typealias TestScopeAction = suspend TestScope.() -> Unit
typealias TestScopeWrappingAction = suspend (TestScopeAction) -> Unit

open class TestSuite internal constructor(
    parent: TestSuite?,
    simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val configurationAction: TestSuiteAction? = null
) : TestScope(parent, simpleNameOrNull, configuration) {

    internal val childScopes: MutableList<TestScope> = mutableListOf()

    private var beforeFirstScopeAction: TestSuiteAction? = null
    private var aroundAllScopesAction: TestScopeWrappingAction? = null
    private var beforeEachScopeAction: TestSuiteAction? = null
    private var aroundEachScopeAction: TestScopeWrappingAction? = null
    private var afterEachScopeAction: TestSuiteAction? = null
    private var afterLastScopeAction: TestSuiteAction? = null

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
        beforeFirstScopeAction = action
    }

    fun aroundAllScopes(action: TestScopeWrappingAction) {
        aroundAllScopesAction = action
    }

    fun beforeEachScope(action: TestSuiteAction) {
        beforeEachScopeAction = action
    }

    fun aroundEachScope(action: TestScopeWrappingAction) {
        aroundEachScopeAction = action
    }

    fun afterEachScope(action: TestSuiteAction) {
        afterEachScopeAction = action
    }

    fun afterLastScope(action: TestSuiteAction) {
        afterLastScopeAction = action
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

        val childScopeActions = allChildScopesWrappingActions().wrappedAround {
            val eachChildScopeWrappingActions = eachChildScopeWrappingActions()

            coroutineScope {
                for (childScope in childScopes) {
                    val wrappedAction = eachChildScopeWrappingActions.wrappedAround {
                        childScope.execute(listener)
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
        }

        withExecutionTracking(listener) {
            withParallelism(effectiveConfiguration.parallelism) {
                childScopeActions()
            }
        }
    }

    private fun List<TestScopeWrappingAction>.wrappedAround(innermostAction: TestScopeAction): TestScopeAction {
        // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters

        return fold<TestScopeWrappingAction, TestScopeAction>(
            { innermostAction() }
        ) { innerAction, wrappingAction ->
            {
                wrappingAction(innerAction)
            }
        }
    }

    /** Returns actions wrapping around each child scope's execution, innermost first. */
    private fun eachChildScopeWrappingActions(): List<TestScopeWrappingAction> = listOfNotNull(
        aroundEachScopeAction,
        beforeAfterWrappingAction(beforeEachScopeAction, afterEachScopeAction)
    )

    /** Returns actions wrapping around all child scope's execution, innermost first. */
    private fun allChildScopesWrappingActions(): List<TestScopeWrappingAction> = listOfNotNull(
        aroundAllScopesAction,
        beforeAfterWrappingAction(beforeFirstScopeAction, afterLastScopeAction)
    )

    /** Returns a wrapping action for a before/after action pair, or null if both are null. */
    private fun beforeAfterWrappingAction(
        beforeAction: TestSuiteAction?,
        afterAction: TestSuiteAction?
    ): TestScopeWrappingAction? {
        if (beforeAction == null && afterAction == null) return null

        return { innerAction: TestScopeAction ->
            beforeAction?.invoke(this)
            innerAction()
            afterAction?.invoke(this)
        }
    }
}
