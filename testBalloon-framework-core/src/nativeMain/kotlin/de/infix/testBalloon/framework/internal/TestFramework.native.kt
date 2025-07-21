package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode
import de.infix.testBalloon.framework.TestSession
import de.infix.testBalloon.framework.internal.integration.IntellijLogTestExecutionReport
import de.infix.testBalloon.framework.internal.integration.ThrowingTestConfigurationReport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import platform.posix.getenv
import kotlin.system.exitProcess
import kotlin.time.Duration

@InvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsWithExceptionHandling {
        @OptIn(ExperimentalForeignApi::class)
        TestSession.global.parameterize(
            EnvironmentBasedElementSelection(getenv("TEST_INCLUDE")?.toKString(), getenv("TEST_EXCLUDE")?.toKString()),
            ThrowingTestConfigurationReport()
        )
    }.onSuccess {
        executeTestsWithExceptionHandling {
            TestSession.global.execute(IntellijLogTestExecutionReport())
        }
    }
}

@Suppress("unused")
@InvokedByGeneratedCode
internal fun configureAndExecuteTestsBlocking(suites: Array<AbstractTestSuite>) {
    runBlocking(Dispatchers.Default) {
        // Why are we running on Dispatchers.Default? Because otherwise, a nested runBlocking could hang the entire
        // system due to thread starvation. See https://github.com/Kotlin/kotlinx.coroutines/issues/3983

        configureAndExecuteTests(suites)
    }
}

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    runTest(timeout = timeout) { action() }
}

internal actual fun handleFrameworkLevelError(throwable: Throwable) {
    exitProcess(3)
}
