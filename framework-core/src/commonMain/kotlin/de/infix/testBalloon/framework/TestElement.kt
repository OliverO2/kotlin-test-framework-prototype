package de.infix.testBalloon.framework

sealed class TestElement(
    override val testElementParent: TestSuite?,
    override val testElementName: String,
    override val testElementDisplayName: String = testElementName,
    var testConfig: TestConfig
) : AbstractTestElement {
    override val testElementPath: TestElementPath
        get() =
            when (testElementParent) {
                null, is TestCompartment, is TestSession -> testElementName
                else -> "${testElementParent?.testElementPath}.$testElementName"
            }

    /**
     * The element's path in a "flattened" form, which external test infrastructure does not split into components.
     * TODO: [flattenedPath] is currently unused. It is intended to be evaluated when producing test reports,
     *     where the external test infrastructure would eagerly split an element's path into an assumed class/function
     *     combination.
     */
    internal val flattenedPath: TestElementPath
        get() =
            when (testElementParent) {
                null, is TestCompartment, is TestSession -> testElementName.spacesEscaped()
                else -> "${testElementParent?.flattenedPath}$spacer${testElementName.spacesEscaped()}"
            }

    private val spacer: Char get() = if (this is Test) '.' else MIDDLE_DOT

    override var testElementIsEnabled: Boolean = true
        internal set

    init {
        @Suppress("LeakingThis")
        testElementParent?.registerChildElement(this)
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
        testElementParent?.let { parent ->
            if (!parent.testElementIsEnabled) testElementIsEnabled = false // Inherit a 'disabled' state
        }
        testConfig.parameterize(this)
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
        testConfig.withReportSetup(this) { additionalReports ->
            suspend fun TestElementEvent.Finished.addToReports() {
                // address reports in reverse order for finish events
                additionalReports?.reversed()?.forEach { it.add(this) }
                report.add(this)
            }

            val startingEvent = TestElementEvent.Starting(this)

            report.add(startingEvent)
            additionalReports?.forEach { it.add(startingEvent) }

            try {
                action()
                TestElementEvent.Finished(this, startingEvent).addToReports()
            } catch (throwable: Throwable) {
                TestElementEvent.Finished(this, startingEvent, throwable).addToReports()
                if (throwable is FailFastException) throw throwable
            }
        }
    }

    override fun toString(): String = "${this::class.simpleName}($testElementPath)"

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
