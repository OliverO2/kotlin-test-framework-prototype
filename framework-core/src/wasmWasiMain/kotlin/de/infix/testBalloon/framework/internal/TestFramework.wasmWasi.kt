package de.infix.testBalloon.framework.internal

import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.InvokedByGeneratedCode
import de.infix.testBalloon.framework.TestElement
import de.infix.testBalloon.framework.TestSession
import de.infix.testBalloon.framework.internal.integration.IntellijLogTestReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Duration

@InvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        // WORKAROUND Wasm/WASI: calling delay() silently exits wasmWasiNodeRun
        //     https://github.com/Kotlin/kotlinx.coroutines/issues/4239
        withContext(Dispatchers.Default) { }

        TestSession.global.parameterize(argumentsBasedElementSelection ?: TestElement.AllInSelection)
    }.onSuccess {
        executeTestsCatching {
            TestSession.global.execute(IntellijLogTestReport())
        }
    }
}

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    runTest(timeout = timeout) { action() }
}
