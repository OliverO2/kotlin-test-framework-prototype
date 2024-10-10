package testFramework.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.getenv
import testFramework.TestSession
import testFramework.internal.integration.IntellijTestLog

actual suspend fun runTests(vararg suites: Any) {
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

internal fun runTestsBlocking(vararg suites: Any) {
    runBlocking { runTests(*suites) }
}
