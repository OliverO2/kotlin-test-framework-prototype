package testFramework

import kotlinx.coroutines.Dispatchers
import testFramework.internal.TestEvent
import testFramework.internal.TestReport

sealed class TestElement(
    override val parentSuite: TestSuite?,
    override val elementName: String,
    override val displayName: String = elementName,
    configuration: Configuration.() -> Unit = {}
) : AbstractTestElement {
    override val elementPath: TestElementPath get() =
        when (parentSuite) {
            null, is TestCompartment, is TestSession -> elementName
            else -> "${parentSuite?.elementPath}.$elementName"
        }

    class Configuration {
        var isEnabled: Boolean = true // children inherit a disabled state

        var context: TestContext = TestContext

        internal fun inheritFrom(parent: Configuration?) {
            if (parent != null) {
                if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            }
        }

        companion object {
            val Default: Configuration.() -> Unit = {
                context = TestContext.invocation(InvocationContext.Mode.SEQUENTIAL)
                    .coroutineContext(Dispatchers.Default)
                    .testScope(true)
            }
        }
    }

    internal var effectiveConfiguration: Configuration = Configuration().apply {
        configuration()
    }

    override val isEnabled by effectiveConfiguration::isEnabled

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
