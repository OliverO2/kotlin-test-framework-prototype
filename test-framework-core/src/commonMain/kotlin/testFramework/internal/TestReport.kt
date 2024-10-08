package testFramework.internal

import kotlinx.datetime.Clock
import testFramework.TestElement

/**
 * A report containing a sequence of test events, each of which will be added via [add] during execution.
 *
 * If [feedMode] is [FeedMode.ALL_ELEMENTS], the report will receive [TestEvent.Starting] and [TestEvent.Finished]
 * for each element, whether the element is disabled or not.
 *
 * If [feedMode] is [FeedMode.ENABLED_ELEMENTS], only enabled elements will receive the above events. The report for a
 * disabled element will receive a single [TestEvent.Finished], and none of the element's descendants will be reported.
 */
internal abstract class TestReport(val feedMode: FeedMode) {
    enum class FeedMode {
        ALL_ELEMENTS,
        ENABLED_ELEMENTS
    }

    abstract suspend fun add(event: TestEvent)
}

internal sealed class TestEvent(val element: TestElement) {
    val instant = Clock.System.now()

    class Starting(element: TestElement) : TestEvent(element)

    class Finished(element: TestElement, val startingEvent: Starting, val throwable: Throwable? = null) :
        TestEvent(element) {
        override fun toString(): String = "${super.toString()} – throwable=$throwable"
    }

    class Skipped(element: TestElement) : TestEvent(element)

    override fun toString(): String = "$element: ${this::class.simpleName}"
}
