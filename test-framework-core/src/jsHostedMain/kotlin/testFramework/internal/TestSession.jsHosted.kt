package testFramework.internal

import testFramework.internal.integration.runTestsKotlinJs

actual suspend fun runTests(vararg suites: Any) {
    // `suites` is unused because test suites register themselves with `TestSession`.
    runTestsKotlinJs()
}
