package testFramework.internal

import testFramework.internal.integration.configureAndRunJsHostedTests

actual suspend fun runTests(vararg suites: Any) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    executeTestsCatching {
        configureAndRunJsHostedTests()
    }
}
