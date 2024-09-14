package testFramework

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.internal.withParallelism
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

typealias TestSuiteConfigurationAction<Fixture> = TestSuite<Fixture>.() -> Unit
typealias TestSuiteAction<Fixture> = suspend TestSuite<Fixture>.() -> Unit
typealias TestSuiteWrappingAction<Fixture> = suspend (TestSuiteAction<Fixture>) -> Unit
typealias TestSuiteNewFixtureAction<Fixture> = suspend TestSuite<Fixture>.() -> Fixture

typealias BasicTestSuite = TestSuite<Nothing>

open class TestSuite<Fixture : Any> internal constructor(
    parent: TestSuite<*>?,
    simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val configurationAction: TestSuiteConfigurationAction<Fixture>? = null
) : TestScope(parent, simpleNameOrNull, configuration) {

    internal val childScopes: MutableList<TestScope> = mutableListOf()

    private var allScopesFixtureSetup: TestSuiteWrappingAction<Fixture>? = null
    private var beforeFirstScopeAction: TestSuiteAction<Fixture>? = null
    private var aroundAllScopesAction: TestSuiteWrappingAction<Fixture>? = null
    private var eachScopeFixtureSetup: TestSuiteWrappingAction<Fixture>? = null
    private var beforeEachScopeAction: TestSuiteAction<Fixture>? = null
    private var aroundEachScopeAction: TestSuiteWrappingAction<Fixture>? = null
    private var afterEachScopeAction: TestSuiteAction<Fixture>? = null
    private var afterLastScopeAction: TestSuiteAction<Fixture>? = null

    init {
        @Suppress("LeakingThis")
        parent?.registerChildScope(this)
    }

    protected constructor(module: TestModule, configurationAction: TestSuiteConfigurationAction<Fixture>) :
        this(parent = module, configurationAction = configurationAction)

    protected constructor(configurationAction: TestSuiteConfigurationAction<Fixture>) :
        this(parent = TestModule.default, configurationAction = configurationAction)

    internal fun registerChildScope(childScope: TestScope) {
        childScopes.add(childScope)
    }

    class FixtureContextElement<Fixture>(val fixture: Fixture) : CoroutineContext.Element {
        companion object : CoroutineContext.Key<FixtureContextElement<*>>

        override val key: CoroutineContext.Key<*>
            get() = FixtureContextElement
    }

    fun fixtureForAll(fixture: TestSuiteNewFixtureAction<Fixture>) {
        allScopesFixtureSetup = fixtureWrappingAction(fixture)
    }

    fun fixtureForEach(fixture: TestSuiteNewFixtureAction<Fixture>) {
        eachScopeFixtureSetup = fixtureWrappingAction(fixture)
    }

    private fun fixtureWrappingAction(fixture: TestSuiteNewFixtureAction<Fixture>): TestSuiteWrappingAction<Fixture> =
        { innerAction: TestSuiteAction<Fixture> ->
            @Suppress("NAME_SHADOWING")
            val fixture = fixture.invoke(this)
            withContext(FixtureContextElement(fixture)) {
                if (fixture is AutoCloseable) {
                    fixture.use {
                        innerAction()
                    }
                } else {
                    innerAction()
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    suspend fun fixture(): Fixture = (
        coroutineContext[FixtureContextElement]?.fixture
            ?: throw IllegalArgumentException("A fixture has not been registered: $coroutineContext")
        ) as Fixture

    fun beforeFirstScope(action: TestSuiteAction<Fixture>) {
        beforeFirstScopeAction = action
    }

    fun aroundAllScopes(action: TestSuiteWrappingAction<Fixture>) {
        aroundAllScopesAction = action
    }

    fun beforeEachScope(action: TestSuiteAction<Fixture>) {
        beforeEachScopeAction = action
    }

    fun aroundEachScope(action: TestSuiteWrappingAction<Fixture>) {
        aroundEachScopeAction = action
    }

    fun afterEachScope(action: TestSuiteAction<Fixture>) {
        afterEachScopeAction = action
    }

    fun afterLastScope(action: TestSuiteAction<Fixture>) {
        afterLastScopeAction = action
    }

    fun suite(name: String, configurationAction: TestSuiteConfigurationAction<Fixture>) {
        TestSuite(this, name, configurationAction = configurationAction)
    }

    fun test(name: String, configuration: TestScopeConfiguration.() -> Unit = {}, action: TestAction<Fixture>) {
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

    private fun List<TestSuiteWrappingAction<Fixture>>.wrappedAround(
        innermostAction: TestSuiteAction<Fixture>
    ): TestSuiteAction<Fixture> = fold(innermostAction) { innerAction, wrappingAction ->
        { wrappingAction(innerAction) }
    }

    /** Returns actions wrapping around each child scope's execution, innermost first. */
    private fun eachChildScopeWrappingActions(): List<TestSuiteWrappingAction<Fixture>> = listOfNotNull(
        aroundEachScopeAction,
        beforeAfterWrappingAction(beforeEachScopeAction, afterEachScopeAction),
        eachScopeFixtureSetup
    )

    /** Returns actions wrapping around all child scope's execution, innermost first. */
    private fun allChildScopesWrappingActions(): List<TestSuiteWrappingAction<Fixture>> = listOfNotNull(
        aroundAllScopesAction,
        beforeAfterWrappingAction(beforeFirstScopeAction, afterLastScopeAction),
        allScopesFixtureSetup
    )

    /** Returns a wrapping action for a before/after action pair, or null if both are null. */
    private fun beforeAfterWrappingAction(
        beforeAction: TestSuiteAction<Fixture>?,
        afterAction: TestSuiteAction<Fixture>?
    ): TestSuiteWrappingAction<Fixture>? {
        if (beforeAction == null && afterAction == null) return null

        return { innerAction: TestSuiteAction<Fixture> ->
            beforeAction?.invoke(this)
            innerAction()
            afterAction?.invoke(this)
        }
    }
}
