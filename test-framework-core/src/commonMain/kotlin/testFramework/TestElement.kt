package testFramework

import testFramework.internal.TestEvent
import testFramework.internal.TestReport

sealed class TestElement(
    override val parentSuite: TestSuite?,
    simpleNameOrNull: String?,
    configuration: TestElementConfiguration.() -> Unit = {}
) : AbstractTestElement {
    override val displayName: String by lazy {
        simpleNameOrNull?.prefixesRemoved() ?: this::class.simpleName ?: "[TestElement]"
    }

    override val elementPath: TestElementPath get() =
        if (parentSuite != null) "${parentSuite?.elementPath}.$displayName" else displayName

    internal var effectiveConfiguration: TestElementConfiguration = TestElementConfiguration().apply {
        configuration()
        if (simpleNameOrNull?.startsWith('!') == true) isEnabled = false
        if (simpleNameOrNull?.startsWith("f:") == true) isFocused = true
    }

    override val isEnabled by effectiveConfiguration::isEnabled
    open val isFocused by effectiveConfiguration::isFocused

    init {
        @Suppress("LeakingThis")
        parentSuite?.registerChildElement(this)
    }

    internal interface Selection {
        fun includes(testElement: TestElement): Boolean
    }

    internal open fun configure(selection: Selection) {
        effectiveConfiguration.inheritFrom(parentSuite?.effectiveConfiguration)
    }

    internal abstract suspend fun execute(report: TestReport)

    internal suspend fun executeReporting(report: TestReport, action: suspend () -> Unit) {
        val startingEvent = TestEvent.Starting(this)

        report.add(startingEvent)

        try {
            action()
            report.add(TestEvent.Finished(this, startingEvent))
        } catch (assertionError: AssertionError) {
            report.add(TestEvent.Finished(this, startingEvent, assertionError))
        } catch (throwable: Throwable) {
            report.add(TestEvent.Finished(this, startingEvent, throwable))
            throw throwable
        }
    }

    override fun toString(): String = "${this::class.simpleName}($elementPath)"

    internal companion object {
        internal val AllInSelection = object : Selection {
            override fun includes(testElement: TestElement): Boolean = true
        }
    }
}

private fun String.prefixesRemoved(): String = when {
    startsWith("f:") -> this.substring(2)
    startsWith('!') -> this.substring(1)
    else -> this
}
