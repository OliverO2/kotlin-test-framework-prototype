package testFramework

import testFramework.internal.TestElementEvent
import testFramework.internal.TestReport

sealed class TestElement(
    override val parentSuite: TestSuite?,
    override val elementName: String,
    override val displayName: String = elementName,
    configuration: Configuration.() -> Unit
) : AbstractTestElement {
    override val elementPath: TestElementPath get() =
        when (parentSuite) {
            null, is TestCompartment, is TestSession -> elementName
            else -> "${parentSuite?.elementPath}.$elementName"
        }

    /**
     * The element's path in a "flattened" form, which external test infrastructure does not split into components.
     * TODO: [flattenedElementPath] is currently unused. It is intended to be evaluated when producing test reports,
     *     where the external test infrastructure would eagerly split an element's path into an assumed class/function
     *     combination.
     */
    val flattenedElementPath: TestElementPath get() =
        when (parentSuite) {
            null, is TestCompartment, is TestSession -> elementName.spacesEscaped()
            else -> "${parentSuite?.flattenedElementPath}$spacer${elementName.spacesEscaped()}"
        }

    private val spacer: Char get() = if (this is Test) '.' else MIDDLE_DOT

    /**
     * A test element's configuration.
     *
     * Most configuration resides in an element's [TestContext]. What is not specifically configured there, is
     * inherited from the [TestContext]s of its parent elements.
     *
     * An exception is the [isEnabled] state, which resides directly in the [Configuration], but is overridden by
     * a disabled parent via [TestElement.configure].
     */
    class Configuration {
        var isEnabled: Boolean = true // children inherit a disabled state
            set(isEnabled) {
                // Restrict changes to disabling, never enable what has been disabled before (e.g. by inheriting a
                // "disabled" setting from a parent). TODO: Ignoring an "enabled" setting is hard to reason about.
                if (!isEnabled) field = false
            }

        var context: TestContext = TestContext

        internal fun inheritFrom(parent: Configuration?) {
            if (parent != null) {
                if (!parent.isEnabled) isEnabled = false // Inherit a 'disabled' state
            }
        }
    }

    /**
     * The effective configuration of `this` element. It is valid after invoking [configure].
     */
    internal var effectiveConfiguration: Configuration = Configuration().apply {
        configuration()
    }

    override val isEnabled by effectiveConfiguration::isEnabled

    init {
        @Suppress("LeakingThis")
        parentSuite?.registerChildElement(this)
    }

    /**
     * A strategy deciding which elements to include in a test execution.
     */
    internal interface Selection {
        fun includes(testElement: TestElement): Boolean
    }

    /**
     * Finalizes the configuration of this test element, preparing it for execution.
     *
     * The framework invokes this method for all test elements before creating an execution plan.
     */
    internal open fun configure(selection: Selection) {
        effectiveConfiguration.inheritFrom(parentSuite?.effectiveConfiguration)
    }

    /**
     * Executes the test element, adding [TestElementEvent]s to the [report].
     *
     * For proper reporting, this method is also invoked for disabled elements.
     */
    internal abstract suspend fun execute(report: TestReport)

    /**
     * Executes [action], reporting its [TestElementEvent]s to the [report].
     */
    internal suspend fun executeReporting(report: TestReport, action: suspend () -> Unit) {
        val startingEvent = TestElementEvent.Starting(this)

        report.add(startingEvent)

        try {
            action()
            report.add(TestElementEvent.Finished(this, startingEvent))
        } catch (assertionError: AssertionError) {
            report.add(TestElementEvent.Finished(this, startingEvent, assertionError))
        } catch (throwable: Throwable) {
            report.add(TestElementEvent.Finished(this, startingEvent, throwable))
            throw throwable
        }
    }

    override fun toString(): String = "${this::class.simpleName}($elementPath)"

    internal companion object {
        /**
         * A [TestElement.Selection] including all test elements.
         */
        internal val AllInSelection = object : Selection {
            override fun includes(testElement: TestElement): Boolean = true
        }
    }
}

private fun String.spacesEscaped(): String = replace(' ', NON_BREAKING_SPACE)

private const val NON_BREAKING_SPACE = '\u00a0'
private const val MIDDLE_DOT = '\u00b7'
