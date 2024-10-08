package testFramework.internal

import testFramework.TestSession
import testFramework.TestSuite

/**
 * Initialize the test framework with a [TestSession].
 *
 * The framework invokes this function before creating any top-level test suites.
 */
internal fun initializeTestFramework(testSession: TestSession?) {
    if (testSession == null) TestSession()
}

/**
 * Discover and execute tests.
 *
 * The framework invokes this function with a list of top-level [TestSuite]s.
 */
expect suspend fun runTests(vararg suites: Any)
