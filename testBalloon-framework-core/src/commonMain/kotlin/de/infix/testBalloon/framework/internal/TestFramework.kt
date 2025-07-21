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

/**
 * Executes [action] to configure tests, handling errors at the framework level.
 *
 * On failure, this function will attempt to exit the process or throw an exception. However, this may not be
 * possible on all platforms. It is the invoker's responsibility to return immediately if this function
 * returns a failure result.
 */
internal inline fun <R> configureTestsWithExceptionHandling(action: () -> R): Result<R> = runCatching {
    action()
}.onFailure { throwable ->
    throwable.logErrorWithStacktrace("Could not configure tests.")
    handleFrameworkLevelError(throwable)
}

/**
 * Executes [action] to run tests, handling errors at the framework level.
 *
 * On failure, this function will attempt to exit the process or throw an exception. However, this may not be
 * possible on all platforms. It is the invoker's responsibility to return immediately if this function
 * returns a failure result.
 */
internal inline fun <R> executeTestsWithExceptionHandling(action: () -> R): Result<R> = runCatching {
    action()
}.onFailure { throwable ->
    throwable.logErrorWithStacktrace("Test framework failure during execution.")
    handleFrameworkLevelError(throwable)
}

/**
 * Handles a framework-level error, aborting the execution with a failure status if possible.
 */
internal expect fun handleFrameworkLevelError(throwable: Throwable)
