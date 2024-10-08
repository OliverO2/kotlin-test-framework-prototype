package testFramework

import testFramework.internal.TestEvent
import testFramework.internal.TestReport

sealed class TestElement(
    internal open val parent: TestSuite?,
    private val simpleNameOrNull: String?,
    configuration: TestElementConfiguration.() -> Unit = {}
) {
    val simpleElementName: String by lazy {
        simpleNameOrNull?.prefixesRemoved() ?: this::class.simpleName ?: "[TestElement]"
    }

    val elementName: String get() = if (parent !=
        null
    ) {
        "${parent?.elementName}.$simpleElementName"
    } else {
        simpleElementName
    }

    protected var effectiveConfiguration: TestElementConfiguration = TestElementConfiguration().apply {
        configuration()
        if (simpleNameOrNull?.startsWith('!') == true) isEnabled = false
        if (simpleNameOrNull?.startsWith("f:") == true) isFocused = true
    }

    var isEnabled by effectiveConfiguration::isEnabled
    var isFocused by effectiveConfiguration::isFocused
    var isSequential by effectiveConfiguration::isSequential
    var parallelism by effectiveConfiguration::parallelism

    init {
        @Suppress("LeakingThis")
        parent?.registerChildElement(this)
    }

    internal open fun configure() {
        effectiveConfiguration.inheritFrom(parent?.effectiveConfiguration)
    }

    internal abstract suspend fun execute(report: TestReport)

    internal suspend fun executeReporting(report: TestReport, action: suspend () -> Unit) {
        if (!isEnabled && report.feedMode == TestReport.FeedMode.ENABLED_ELEMENTS) {
            report.add(TestEvent.Skipped(this))
            return
        }

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

    override fun toString(): String = "${this::class.simpleName}($elementName)"
}

private fun String.prefixesRemoved(): String = when {
    startsWith("f:") -> this.substring(2)
    startsWith('!') -> this.substring(1)
    else -> this
}
