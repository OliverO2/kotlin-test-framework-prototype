package testFramework.internal

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import testFramework.AbstractTestSuite
import testFramework.TestElement
import testFramework.TestFrameworkInvokedByGeneratedCode
import testFramework.TestSession
import testFramework.internal.integration.IntellijLogTestReport
import kotlin.time.Duration

@TestFrameworkInvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    // This function is intended for internal framework testing only:
    // On the JVM, tests will be discovered and executed via JUnit Platform, which means that this function
    // will not be used.

    configureTestsCatching {
        TestSession.global.configure(argumentsBasedElementSelection ?: TestElement.AllInSelection)
    }.onSuccess {
        executeTestsCatching {
            TestSession.global.execute(IntellijLogTestReport)
        }
    }
}

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    runTest(timeout = timeout) { action() }
}
