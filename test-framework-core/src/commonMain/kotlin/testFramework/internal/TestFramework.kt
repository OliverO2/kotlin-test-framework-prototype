package testFramework.internal

import kotlinx.coroutines.test.TestScope
import testFramework.AbstractTestSession
import testFramework.AbstractTestSuite
import testFramework.TestElement
import testFramework.TestFrameworkInvokedByGeneratedCode
import testFramework.TestSession
import testFramework.TestSuite
import kotlin.time.Duration

/**
 * Initializes the test framework with a [TestSession].
 *
 * The framework invokes this function before creating any top-level test suites (which become children of the
 * [TestSession]).
 */
@TestFrameworkInvokedByGeneratedCode
internal fun initializeTestFramework(testSession: AbstractTestSession?, arguments: Array<String>? = null) {
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
@TestFrameworkInvokedByGeneratedCode
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
