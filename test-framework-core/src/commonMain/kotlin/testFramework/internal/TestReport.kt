package testFramework.internal

import kotlinx.datetime.Clock
import testFramework.TestScope

/**
 * A report containing a sequence of test events, each of which will be added via [add] during execution.
 *
 * If [feedMode] is [FeedMode.ALL_SCOPES], the report will receive [TestEvent.Starting] and [TestEvent.Finished]
 * for each scope, whether the scope is disabled or not.
 *
 * If [feedMode] is [FeedMode.ENABLED_SCOPES], only enabled scopes will receive the above events. The report for a
 * disabled scope will receive a single [TestEvent.Finished], and none of the scope's descendants will be reported.
 */
internal abstract class TestReport(val feedMode: FeedMode) {
    enum class FeedMode {
        ALL_SCOPES,
        ENABLED_SCOPES
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
