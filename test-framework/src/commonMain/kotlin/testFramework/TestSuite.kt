package testFramework

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import testFramework.internal.TestSession
import testFramework.internal.withParallelism
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

typealias TestSuiteComponentsDefinition<Fixture> = TestSuite<Fixture>.() -> Unit
typealias TestSuiteAction<Fixture> = suspend TestSuite<Fixture>.() -> Unit
typealias TestSuiteWrappingAction<Fixture> = suspend (TestSuiteAction<Fixture>) -> Unit
typealias TestSuiteNewFixtureAction<Fixture> = suspend TestSuite<Fixture>.() -> Fixture

typealias BasicTestSuite = TestSuite<Nothing>

open class TestSuite<Fixture : Any> internal constructor(
    parent: TestSuite<*>?,
    simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val componentsDefinition: TestSuiteComponentsDefinition<Fixture>? = null
) : TestScope(parent, simpleNameOrNull, configuration) {

    internal val childScopes: MutableList<TestScope> = mutableListOf()

    private var allScopesFixtureSetup: TestSuiteWrappingAction<Fixture>? = null
    private var aroundAllAction: TestSuiteWrappingAction<Fixture>? = null

    protected constructor(componentsDefinition: TestSuiteComponentsDefinition<Fixture>) :
        this(parent = TestSession, componentsDefinition = componentsDefinition)

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

    fun aroundAll(action: TestSuiteWrappingAction<Fixture>) {
        aroundAllAction = action
    }

    fun suite(name: String, componentsDefinition: TestSuiteComponentsDefinition<Fixture>) {
        TestSuite(this, name, componentsDefinition = componentsDefinition)
    }

    fun test(name: String, configuration: TestScopeConfiguration.() -> Unit = {}, action: TestAction<Fixture>) {
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

    override suspend fun execute(listener: TestScopeEventListener?) {
        if (!scopeIsEnabled) {
            trackSkipping(listener)
            // This is opinionated, following JUnit Platform TestEngine guidelines in
            // https://junit.org/junit5/docs/current/user-guide/#test-engines-custom:
            // > If a node is reported as skipped, there must not be any events reported for its descendants.
            return
        }

        val childScopeActions = allChildScopesWrappingActions().wrappedAround {
            coroutineScope {
                for (childScope in childScopes) {
                    if (effectiveConfiguration.isSequential == true) {
                        childScope.execute(listener)
                    } else {
                        launch {
                            childScope.execute(listener)
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

    /** Returns actions wrapping around all child scope's execution, innermost first. */
    private fun allChildScopesWrappingActions(): List<TestSuiteWrappingAction<Fixture>> = listOfNotNull(
        aroundAllAction,
        allScopesFixtureSetup
    )
}
