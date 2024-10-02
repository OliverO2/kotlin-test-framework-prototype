package testFramework.internal

import kotlinx.coroutines.runBlocking
import testFramework.internal.integration.IntellijTestLog

actual suspend fun runTests(vararg suites: Any) {
    // `suites` is unused because test suites register themselves with `TestSession`.
    TestSession.configure()
    TestSession.execute(IntellijTestLog)
}

internal fun runTestsBlocking(vararg suites: Any) {
    runBlocking { runTests(*suites) }
}
