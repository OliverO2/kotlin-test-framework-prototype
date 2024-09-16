package testFramework

import kotlinx.datetime.Clock

sealed class TestScope(
    internal open val parent: TestSuite<*>?,
    private val simpleNameOrNull: String?,
    configuration: TestScopeConfiguration.() -> Unit = {}
) {
    val simpleScopeName: String by lazy {
        // For proper reporting of multi-target test runs, `simpleScopeName` for top-level modules (children of `Root`)
        // must be unique across platform targets.
        fun String.withPlatformIfRootChild() =
            if (parent != null && parent?.parent == null) "$this(${testPlatform.displayName})" else this

        simpleNameOrNull?.prefixesRemoved() ?: (this::class.simpleName ?: "[TestScope]").withPlatformIfRootChild()
    }

    val scopeName: String get() = if (parent != null) "${parent?.scopeName}.$simpleScopeName" else simpleScopeName

    protected var effectiveConfiguration: TestScopeConfiguration = TestScopeConfiguration().apply {
        configuration()
        if (simpleNameOrNull?.startsWith('!') == true) isEnabled = false
        if (simpleNameOrNull?.startsWith("f:") == true) isFocused = true
    }

    var scopeIsEnabled by effectiveConfiguration::isEnabled
    var scopeIsFocused by effectiveConfiguration::isFocused
    var scopeIsSequential by effectiveConfiguration::isSequential
    var scopeParallelism by effectiveConfiguration::parallelism

    init {
        @Suppress("LeakingThis")
        parent?.registerChildScope(this)
    }

    internal open fun configure() {
        effectiveConfiguration.inheritFrom(parent?.effectiveConfiguration)
    }

    internal abstract suspend fun execute(listener: TestScopeEventListener?)

    internal sealed class Event(val scope: TestScope) {
        val instant = Clock.System.now()

        class Starting(scope: TestScope) : Event(scope)

        class Finished(scope: TestScope, val startingEvent: Starting, val throwable: Throwable? = null) :
            Event(scope) {
            override fun toString(): String = "${super.toString()} – throwable=$throwable"
        }

        class Skipped(scope: TestScope) : Event(scope)

        override fun toString(): String = "$scope: ${this::class.simpleName}"
    }

    internal suspend fun withExecutionTracking(listener: TestScopeEventListener?, action: suspend () -> Unit) {
        if (listener == null) return action()

        val startingEvent = Event.Starting(this)

        listener(startingEvent)

        try {
            action()
            listener(Event.Finished(this, startingEvent))
        } catch (assertionError: AssertionError) {
            listener(Event.Finished(this, startingEvent, assertionError))
        } catch (throwable: Throwable) {
            listener(Event.Finished(this, startingEvent, throwable))
            throw throwable
        }
    }

    internal suspend fun trackSkipping(listener: TestScopeEventListener?) {
        listener?.invoke(Event.Skipped(this))
    }

    override fun toString(): String = "${this::class.simpleName}($scopeName)"
}

internal typealias TestScopeEventListener = suspend (TestScope.Event) -> Unit

private fun String.prefixesRemoved(): String = when {
    startsWith("f:") -> this.substring(2)
    startsWith('!') -> this.substring(1)
    else -> this
}
