package testFramework.internal

import testFramework.TestSuite
import testFramework.internal.integration.configureAndRunJsHostedTests

internal actual suspend fun runTests(suites: Array<TestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    executeTestsCatching {
        configureAndRunJsHostedTests()
    }
}
