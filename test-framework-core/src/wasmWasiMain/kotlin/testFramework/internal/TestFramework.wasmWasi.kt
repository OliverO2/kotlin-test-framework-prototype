package testFramework.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import testFramework.AbstractTestSuite
import testFramework.TestElement
import testFramework.TestFrameworkInvokedByGeneratedCode
import testFramework.TestSession
import testFramework.internal.integration.IntellijLogTestReport
import kotlin.time.Duration

@TestFrameworkInvokedByGeneratedCode
internal actual suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        // WORKAROUND Wasm/WASI: calling delay() silently exits wasmWasiNodeRun
        //     https://github.com/Kotlin/kotlinx.coroutines/issues/4239
        withContext(Dispatchers.Default) { }

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
