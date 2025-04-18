package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSession
import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode
import de.infix.testBalloon.framework.TestCompartment
import de.infix.testBalloon.framework.TestElement
import de.infix.testBalloon.framework.TestSession
import de.infix.testBalloon.framework.TestSuite
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration

/**
 * The test framework's global settings.
 */
internal object TestFramework {
    /** Resets the framework's global state, enabling the execution of multiple test sessions in one process. */
    internal fun resetState() {
        TestCompartment.resetState()
        TestSession.resetState()
        argumentsBasedElementSelection = null
    }
}

/**
 * Initializes the test framework with a [TestSession].
 *
 * The framework invokes this function before creating any top-level [TestSuite].
 */
@InvokedByGeneratedCode
internal fun initializeTestFramework(testSession: AbstractTestSession? = null, arguments: Array<String>? = null) {
    if (!arguments.isNullOrEmpty()) {
        argumentsBasedElementSelection = ArgumentsBasedElementSelection(arguments)
    }
    if (testSession == null) TestSession()
}

/**
 * Configures and executes tests.
 *
 * The framework invokes this function with a list of top-level [TestSuite]s.
 */
@InvokedByGeneratedCode
internal expect suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>)

/**
 * An argument-based element selection, if existing, or null.
 *
 * Initialized in [initializeTestFramework], used in [configureAndExecuteTests].
 */
internal var argumentsBasedElementSelection: TestElement.Selection? = null

/**
 * Runs the test [action] on [this] TestScope via [kotlinx.coroutines.test.runTest], waiting for its completion.
 *
 * On (JS) platforms using a `Promise` to run a test asynchronously, an implementation of this method must `await`
 * the Promise before returning.
 */
internal expect suspend fun TestScope.runTestAwaitingCompletion(timeout: Duration, action: suspend TestScope.() -> Unit)
