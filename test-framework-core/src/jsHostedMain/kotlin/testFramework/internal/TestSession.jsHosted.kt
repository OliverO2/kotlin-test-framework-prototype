package testFramework.internal

import testFramework.AbstractTestSuite
import testFramework.internal.integration.configureAndRunJsHostedTests

internal actual suspend fun runTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    executeTestsCatching {
        configureAndRunJsHostedTests()
    }
}
