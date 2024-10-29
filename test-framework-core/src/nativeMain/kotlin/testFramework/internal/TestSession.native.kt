package testFramework.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import testFramework.AbstractTestSuite
import testFramework.TestSession
import testFramework.internal.integration.IntellijTestLog

internal actual suspend fun runTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        @OptIn(ExperimentalForeignApi::class)
        TestSession.global.configure(
            EnvironmentBasedElementSelection(getenv("TEST_INCLUDE")?.toKString(), getenv("TEST_EXCLUDE")?.toKString())
        )
    }.onSuccess {
        executeTestsCatching {
            TestSession.global.execute(IntellijTestLog)
        }
    }
}

internal fun runTestsBlocking(suites: Array<AbstractTestSuite>) {
    runBlocking { runTests(suites) }
}
