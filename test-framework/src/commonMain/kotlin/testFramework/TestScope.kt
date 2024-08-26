package testFramework

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import testFramework.internal.withParallelism

typealias TestScopeDefinitionAction = TestScope.() -> Unit

open class TestScope internal constructor(
    internal open val parent: TestScope?,
    private val simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val definitionAction: TestScopeDefinitionAction? = null
) {
    val simpleScopeName: String get() = simpleNameOrNull?.prefixesRemoved() ?: this::class.simpleName ?: "[TestScope]"
    val scopeName: String get() =
        (if (parent != null) "${parent?.scopeName}." else "") + simpleScopeName

    protected var effectiveConfiguration: TestScopeConfiguration = TestScopeConfiguration().apply {
        configuration()
        if (simpleNameOrNull?.startsWith('!') == true) isEnabled = false
        if (simpleNameOrNull?.startsWith("f:") == true) isFocused = true
    }

    var scopeIsEnabled by effectiveConfiguration::isEnabled
    var scopeIsFocused by effectiveConfiguration::isFocused
    var scopeParallelism by effectiveConfiguration::parallelism

    internal val subScopes: MutableList<TestScope> = mutableListOf()

    init {
        @Suppress("LeakingThis")
        parent?.registerSubScope(this)
    }

    protected constructor(module: TestModule, definitionAction: TestScopeDefinitionAction) :
        this(parent = module, definitionAction = definitionAction)

    protected constructor(definitionAction: TestScopeDefinitionAction) :
        this(TestModule.default, definitionAction = definitionAction)

    private fun registerSubScope(subScope: TestScope) {
        require(this !is Test) { "the test $scopeName must not have sub-scopes" }
        subScopes.add(subScope)
    }

    fun beforeFirstScope(action: TestScopeInvocationAction) {
        requireAcceptsSubScopeAction()
        effectiveConfiguration.beforeFirstScopeAction = action
    }

    fun beforeEachScope(action: TestScopeInvocationAction) {
        requireAcceptsSubScopeAction()
        effectiveConfiguration.beforeEachScopeAction = action
    }

    fun aroundEachScope(action: TestScopeWrappingAction) {
        requireAcceptsSubScopeAction()
        effectiveConfiguration.aroundEachScopeAction = action
    }

    fun afterEachScope(action: TestScopeInvocationAction) {
        requireAcceptsSubScopeAction()
        effectiveConfiguration.afterEachScopeAction = action
    }

    fun afterLastScope(action: TestScopeInvocationAction) {
        requireAcceptsSubScopeAction()
        effectiveConfiguration.afterLastScopeAction = action
    }

    private fun requireAcceptsSubScopeAction() {
        require(this !is Test) { "the test $scopeName must not have sub-scope actions" }
    }

    fun scope(name: String, definitionAction: TestScopeDefinitionAction) {
        TestScope(this, name, definitionAction = definitionAction)
    }

    fun test(
        name: String,
        configuration: TestScopeConfiguration.() -> Unit = {
        },
        invocationAction: TestScopeInvocationAction
    ) {
        Test(this, name, configuration = configuration, invocationAction)
    }

    class Invocation internal constructor(val scope: TestScope, val listener: (Event) -> Unit) {
        sealed class Event(val scope: TestScope) {
            val instant = Clock.System.now()

            class Starting(scope: TestScope) : Event(scope)
            class Finished(scope: TestScope, val startingEvent: Starting, val throwable: Throwable? = null) :
                Event(scope) {
                override fun toString(): String = "${super.toString()} – throwable=$throwable"
            }
            class Skipped(scope: TestScope) : Event(scope)

            override fun toString(): String = "$scope: ${this::class.simpleName}"
        }

        suspend fun withExecutionTracking(action: suspend () -> Unit) {
            val startingEvent = Event.Starting(scope)

            listener(startingEvent)

            try {
                action()
                listener(Event.Finished(scope, startingEvent))
            } catch (assertionError: AssertionError) {
                listener(Event.Finished(scope, startingEvent, assertionError))
            } catch (throwable: Throwable) {
                listener(Event.Finished(scope, startingEvent, throwable))
                throw throwable
            }
        }

        fun trackSkipping() {
            listener(Event.Skipped(scope))
        }

        override fun toString(): String = scope.scopeName
    }

    fun configure() {
        // Inherit a 'disabled' mode from the parent.
        if (parent?.scopeIsEnabled == false) scopeIsEnabled = false

        if (this is Test) {
            check(subScopes.isEmpty()) { "The test scope $scopeName must not have any sub-scope" }
            return
        }

        definitionAction?.invoke(this)
        require(subScopes.isNotEmpty()) { "The non-test scope $scopeName must have at least one sub-scope" }

        subScopes.forEach { it.configure() }

        if (scopeIsEnabled && subScopes.isNotEmpty()) {
            if (subScopes.any { it.scopeIsFocused }) {
                // Disable all non-focused sub-scopes (disabling does not propagate to transitive sub-scopes).
                subScopes.forEach {
                    it.scopeIsEnabled = it.scopeIsFocused
                }
            } else {
                // Disable this scope if none of its sub-scopes are enabled.
                if (!subScopes.any { it.scopeIsEnabled }) {
                    scopeIsEnabled = false
                }
            }
        }
    }

    open suspend fun execute(outerInvocation: Invocation) {
        val invocation = Invocation(this, outerInvocation.listener)

        if (!scopeIsEnabled) {
            invocation.trackSkipping()
            // This is opinionated, following JUnit Platform TestEngine guidelines in
            // https://junit.org/junit5/docs/current/user-guide/#test-engines-custom:
            // > If a node is reported as skipped, there must not be any events reported for its descendants.
            return
        }

        invocation.withExecutionTracking {
            withParallelism(effectiveConfiguration.parallelism) {
                effectiveConfiguration.beforeFirstScopeAction?.invoke(invocation)

                val subScopeWrappingActions = subScopeWrappingActions()

                coroutineScope {
                    for (subScope in subScopes) {
                        // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters
                        val wrappedAction =
                            subScopeWrappingActions.fold<TestScopeWrappingAction, TestScopeInvocationAction>(
                                subScope::execute
                            ) { innerAction, wrappingAction ->
                                {
                                    wrappingAction(invocation, innerAction)
                                }
                            }

                        launch {
                            wrappedAction.invoke(invocation)
                        }
                    }
                }

                effectiveConfiguration.afterLastScopeAction?.invoke(invocation)
            }
        }
    }

    /** Returns actions wrapping around a sub-scope's execution, innermost first. */
    private fun subScopeWrappingActions(): List<TestScopeWrappingAction> {
        // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters

        fun beforeAfterWrappingAction(): TestScopeWrappingAction? {
            if (effectiveConfiguration.beforeEachScopeAction == null &&
                effectiveConfiguration.afterEachScopeAction == null
            ) {
                return null
            }

            return { invocation: Invocation, innerAction: TestScopeInvocationAction ->
                effectiveConfiguration.beforeEachScopeAction?.invoke(invocation)
                innerAction(invocation)
                effectiveConfiguration.afterEachScopeAction?.invoke(invocation)
            }
        }

        // Return a list of wrapping actions from innermost to outermost.
        return listOfNotNull(
            effectiveConfiguration.aroundEachScopeAction,
            beforeAfterWrappingAction()
        )
    }

    override fun toString(): String = scopeName
}

private fun String.prefixesRemoved(): String = when {
    startsWith("f:") -> this.substring(2)
    startsWith('!') -> this.substring(1)
    else -> this
}
