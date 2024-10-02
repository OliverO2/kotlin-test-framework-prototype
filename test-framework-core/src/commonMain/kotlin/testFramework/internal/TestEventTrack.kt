package testFramework.internal

import kotlinx.datetime.Clock
import testFramework.TestScope

internal abstract class TestEventTrack(val mode: Mode) {
    enum class Mode {
        FULL_HIERARCHY,
        EXCLUDE_SKIPPED_DESCENDANTS
    }

    abstract suspend fun add(event: TestEvent)
}

internal sealed class TestEvent(val scope: TestScope) {
    val instant = Clock.System.now()

    class Starting(scope: TestScope) : TestEvent(scope)

    class Finished(scope: TestScope, val startingEvent: Starting, val throwable: Throwable? = null) :
        TestEvent(scope) {
        override fun toString(): String = "${super.toString()} – throwable=$throwable"
    }

    class Skipped(scope: TestScope) : TestEvent(scope)

    override fun toString(): String = "$scope: ${this::class.simpleName}"
}
