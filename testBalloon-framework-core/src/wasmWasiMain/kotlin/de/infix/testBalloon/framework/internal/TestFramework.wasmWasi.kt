package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode
import de.infix.testBalloon.framework.TestElement
import de.infix.testBalloon.framework.TestSession
import de.infix.testBalloon.framework.internal.integration.IntellijLogTestExecutionReport
import de.infix.testBalloon.framework.internal.integration.ThrowingTestConfigurationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Duration

@InvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    // WORKAROUND Wasm/WASI: calling delay() silently exits wasmWasiNodeRun
    //     https://github.com/Kotlin/kotlinx.coroutines/issues/4239
    withContext(Dispatchers.Default) { }

    configureTestsWithExceptionHandling {
        TestSession.global.parameterize(
            argumentsBasedElementSelection ?: TestElement.AllInSelection,
            ThrowingTestConfigurationReport()
        )
    }.onSuccess {
        executeTestsWithExceptionHandling {
            TestSession.global.execute(IntellijLogTestExecutionReport())
        }
    }
}

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    runTest(timeout = timeout) { action() }
}

internal actual fun handleFrameworkLevelError(throwable: Throwable) {
    // We do nothing here and accept that no process failure will be signalled to the invoker, because:
    // – there is no process exit call in Kotlin's stdlib for Wasm/WASI, and
    // – throwing at this point would suppress the error message.
    // TODO: Find a way to signal a framework-level error status (process failure) to the invoker.
}
