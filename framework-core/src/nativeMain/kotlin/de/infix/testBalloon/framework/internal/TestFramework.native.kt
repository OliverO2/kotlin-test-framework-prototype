package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode
import de.infix.testBalloon.framework.TestSession
import de.infix.testBalloon.framework.internal.integration.IntellijLogTestReport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import platform.posix.getenv
import kotlin.time.Duration

@InvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        @OptIn(ExperimentalForeignApi::class)
        TestSession.global.parameterize(
            EnvironmentBasedElementSelection(getenv("TEST_INCLUDE")?.toKString(), getenv("TEST_EXCLUDE")?.toKString())
        )
    }.onSuccess {
        executeTestsCatching {
            TestSession.global.execute(IntellijLogTestReport())
        }
    }
}

@Suppress("unused")
@InvokedByGeneratedCode
internal fun configureAndExecuteTestsBlocking(suites: Array<AbstractTestSuite>) {
    runBlocking { configureAndExecuteTests(suites) }
}

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    runTest(timeout = timeout) { action() }
}
