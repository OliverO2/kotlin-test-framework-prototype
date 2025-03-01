package testFramework.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import platform.posix.getenv
import testFramework.AbstractTestSuite
import testFramework.TestFrameworkInvokedByGeneratedCode
import testFramework.TestSession
import testFramework.internal.integration.IntellijTestLog
import kotlin.time.Duration

@TestFrameworkInvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
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

@Suppress("unused")
@TestFrameworkInvokedByGeneratedCode
internal fun configureAndExecuteTestsBlocking(suites: Array<AbstractTestSuite>) {
    runBlocking { configureAndExecuteTests(suites) }
}

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    runTest(timeout = timeout) { action() }
}
