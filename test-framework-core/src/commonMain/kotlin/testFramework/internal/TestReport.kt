package testFramework.internal

import kotlinx.datetime.Clock
import testFramework.TestElement

/**
 * A report containing a sequence of test events, each of which will be added via [add] during execution.
 *
 * During execution, a report is expected to contain [TestEvent.Starting] and [TestEvent.Finished] for every
 * element in the element tree. This includes events for disabled elements.
 */
internal abstract class TestReport {
    abstract suspend fun add(event: TestEvent)
}

internal sealed class TestEvent(val element: TestElement) {
    val instant = Clock.System.now()

    class Starting(element: TestElement) : TestEvent(element)

    class Finished(element: TestElement, val startingEvent: Starting, val throwable: Throwable? = null) :
        TestEvent(element) {
        override fun toString(): String = "${super.toString()} – throwable=$throwable"
    }

    override fun toString(): String = "$element: ${this::class.simpleName}"
}
