package testFramework.internal

import kotlinx.coroutines.runBlocking
import testFramework.TestSession
import testFramework.internal.integration.IntellijTestLog

actual suspend fun runTests(vararg suites: Any) {
    // `suites` is unused because test suites register themselves with `TestSession`.
    TestSession.global.configure()
    TestSession.global.execute(IntellijTestLog)
}

internal fun runTestsBlocking(vararg suites: Any) {
    runBlocking { runTests(*suites) }
}
