package testFramework

import testFramework.internal.TestElementEvent
import testFramework.internal.TestReport

sealed class TestElement(
    override val parentSuite: TestSuite?,
    override val elementName: String,
    override val displayName: String = elementName,
    var configuration: TestConfig
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

    override var isEnabled: Boolean = true
        internal set

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
     * Parameterizes this test element, preparing it for execution.
     *
     * The framework invokes this method for all test elements before creating an execution plan.
     */
    internal open fun parameterize(selection: Selection) {
        parentSuite?.let {
            if (!it.isEnabled) isEnabled = false // Inherit a 'disabled' state
        }
        configuration.parameterize(this)
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

internal fun String.spacesEscaped(): String = replace(' ', NON_BREAKING_SPACE)

private const val NON_BREAKING_SPACE = '\u00a0'
private const val MIDDLE_DOT = '\u00b7'
