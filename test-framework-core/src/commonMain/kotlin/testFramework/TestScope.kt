package testFramework

import testFramework.internal.TestEvent
import testFramework.internal.TestEventTrack

sealed class TestScope(
    internal open val parent: TestSuite?,
    private val simpleNameOrNull: String?,
    configuration: TestScopeConfiguration.() -> Unit = {}
) {
    val simpleScopeName: String by lazy {
        simpleNameOrNull?.prefixesRemoved() ?: this::class.simpleName ?: "[TestScope]"
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

    internal abstract suspend fun execute(track: TestEventTrack)

    internal suspend fun executeTracking(track: TestEventTrack, action: suspend () -> Unit) {
        if (!scopeIsEnabled && track.mode == TestEventTrack.Mode.EXCLUDE_SKIPPED_DESCENDANTS) {
            track.add(TestEvent.Skipped(this))
            return
        }

        val startingEvent = TestEvent.Starting(this)

        track.add(startingEvent)

        try {
            action()
            track.add(TestEvent.Finished(this, startingEvent))
        } catch (assertionError: AssertionError) {
            track.add(TestEvent.Finished(this, startingEvent, assertionError))
        } catch (throwable: Throwable) {
            track.add(TestEvent.Finished(this, startingEvent, throwable))
            throw throwable
        }
    }

    override fun toString(): String = "${this::class.simpleName}($scopeName)"
}

private fun String.prefixesRemoved(): String = when {
    startsWith("f:") -> this.substring(2)
    startsWith('!') -> this.substring(1)
    else -> this
}
