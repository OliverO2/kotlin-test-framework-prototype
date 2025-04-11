package testFramework

import kotlinx.datetime.Clock

/**
 * A report containing a sequence of test events, each of which will be [add]ed during execution.
 *
 * During execution, a report is expected to contain [TestElementEvent.Starting] and [TestElementEvent.Finished] for every
 * element in the element hierarchy. This includes events for disabled elements.
 */
abstract class TestReport {
    abstract suspend fun add(event: TestElementEvent)
}

/**
 * An event occurring as part of a test element's execution.
 */
sealed class TestElementEvent(val element: TestElement) {
    val instant = Clock.System.now()

    class Starting(element: TestElement) : TestElementEvent(element)

    class Finished(element: TestElement, val startingEvent: Starting, val throwable: Throwable? = null) :
        TestElementEvent(element) {

        val succeeded: Boolean get() = throwable == null
        val failed: Boolean get() = throwable != null

        override fun toString(): String = "${super.toString()} – throwable=$throwable"
    }

    override fun toString(): String = "$element: ${this::class.simpleName}"
}
