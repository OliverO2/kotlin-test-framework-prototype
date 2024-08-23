package testFramework

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import testFramework.internal.withParallelism
import kotlin.time.Duration

typealias TestScopeDefinitionAction = suspend TestScope.(invocation: TestScope.Invocation) -> Unit

open class TestScope internal constructor(
    protected open val parent: TestScope?,
    private val simpleNameOrNull: String? = null,
    configuration: TestScopeConfiguration.() -> Unit = {},
    private val definitionAction: TestScopeDefinitionAction? = null
) {
    private val initialScopeConfiguration: TestScopeConfiguration = TestScopeConfiguration().apply(configuration)
    private var effectiveScopeConfiguration: TestScopeConfiguration = initialScopeConfiguration.copy()

    val simpleName: String get() = simpleNameOrNull ?: this::class.simpleName ?: "[TestScope]"
    val scopeName: String get() =
        (if (parent != null) "${parent?.scopeName}." else "") + simpleName

    private val isEnabled get() = effectiveScopeConfiguration.isEnabled && !simpleName.startsWith('!')
    private val isFocused get() = simpleName.startsWith("f:")

    val invocationCount: Int by effectiveScopeConfiguration::invocationCount
    val timeout: Duration? by effectiveScopeConfiguration::timeout

    init {
        @Suppress("LeakingThis")
        parent?.registerSubScope(this)
    }

    protected constructor(module: TestModule, definitionAction: TestScopeDefinitionAction) :
        this(parent = module, definitionAction = definitionAction)

    protected constructor(definitionAction: TestScopeDefinitionAction) :
        this(TestModule.default, definitionAction = definitionAction)

    private fun registerSubScope(subScope: TestScope) {
        effectiveScopeConfiguration.subScopes.add(subScope)
    }

    fun beforeFirstScope(action: TestScopeInvocationAction) {
        effectiveScopeConfiguration.beforeFirstScopeAction = action
    }

    fun beforeEachScope(action: TestScopeInvocationAction) {
        effectiveScopeConfiguration.beforeEachScopeAction = action
    }

    fun aroundEachScope(action: TestScopeWrappingAction) {
        effectiveScopeConfiguration.aroundEachScopeAction = action
    }

    fun afterEachScope(action: TestScopeInvocationAction) {
        effectiveScopeConfiguration.afterEachScopeAction = action
    }

    fun afterLastScope(action: TestScopeInvocationAction) {
        effectiveScopeConfiguration.afterLastScopeAction = action
    }

    fun scope(
        name: String,
        configuration: TestScopeConfiguration.() -> Unit = {},
        definitionAction: TestScopeDefinitionAction
    ) {
        TestScope(this, name, configuration = configuration, definitionAction = definitionAction)
    }

    class Invocation internal constructor(val scope: TestScope, val invocationIndex: Int) {
        override fun toString(): String = buildString {
            append(scope.scopeName)
            listOfNotNull(
                scope.effectiveScopeConfiguration.invocationCount.let {
                    if (it > 1) "#${invocationIndex + 1}/${scope.effectiveScopeConfiguration.invocationCount}" else null
                },
                scope.effectiveScopeConfiguration.timeout?.let { "timeout=$it" }
            ).let {
                if (it.isNotEmpty()) {
                    append(it.joinToString(prefix = "(", postfix = ")"))
                }
            }
        }
    }

    suspend fun execute(outerInvocation: Invocation) {
        withParallelism(initialScopeConfiguration.parallelism) {
            for (invocationIndex in 0..<initialScopeConfiguration.invocationCount) {
                val invocation = Invocation(this, invocationIndex)

                // TODO: This defines invocation parameters and executes tests.
                //     - Invocation parameters should be defined once for the scope, then be stable.
                //     - Tests must run with invocation parameters (here: parallelism and invocationCount).
                if (definitionAction != null) {
                    effectiveScopeConfiguration = initialScopeConfiguration.copy()
                    definitionAction.invoke(this, invocation)
                }

                val focusedSubScopes = effectiveScopeConfiguration.subScopes.filter { it.isFocused }
                val candidateSubScopes =
                    focusedSubScopes.ifEmpty { effectiveScopeConfiguration.subScopes }.filter { it.isEnabled }

                if (candidateSubScopes.isEmpty()) return@withParallelism

                effectiveScopeConfiguration.beforeFirstScopeAction?.invoke(invocation)

                val subScopeWrappingActions = subScopeWrappingActions()

                for (subScope in candidateSubScopes) {
                    // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters
                    val wrappedAction =
                        subScopeWrappingActions.fold<TestScopeWrappingAction, TestScopeInvocationAction>(
                            subScope::execute
                        ) { innerAction, wrappingAction ->
                            {
                                wrappingAction(invocation, innerAction)
                            }
                        }

                    wrappedAction.invoke(invocation)
                }

                effectiveScopeConfiguration.afterLastScopeAction?.invoke(invocation)
            }
        }
    }

    /** Returns actions wrapping around a sub-scope's execution, innermost first. */
    private fun subScopeWrappingActions(): List<TestScopeWrappingAction> {
        // WORKAROUND: KT-51067 Function for creating suspending lambdas doesn't allow lambda parameters

        fun timeoutWrappingAction(): TestScopeWrappingAction? {
            val timeout = effectiveScopeConfiguration.timeout ?: return null

            return { invocation: Invocation, innerAction: TestScopeInvocationAction ->
                try {
                    withTimeout(timeout) {
                        innerAction(invocation)
                    }
                } catch (exception: TimeoutCancellationException) {
                    throw AssertionError("$exception")
                }
            }
        }

        fun beforeAfterWrappingAction(): TestScopeWrappingAction? {
            if (effectiveScopeConfiguration.beforeEachScopeAction == null &&
                effectiveScopeConfiguration.afterEachScopeAction == null
            ) {
                return null
            }

            return { invocation: Invocation, innerAction: TestScopeInvocationAction ->
                effectiveScopeConfiguration.beforeEachScopeAction?.invoke(invocation)
                innerAction(invocation)
                effectiveScopeConfiguration.afterEachScopeAction?.invoke(invocation)
            }
        }

        // Return a list of wrapping actions from innermost to outermost.
        return listOfNotNull(
            effectiveScopeConfiguration.aroundEachScopeAction,
            beforeAfterWrappingAction(),
            timeoutWrappingAction()
        )
    }
}
