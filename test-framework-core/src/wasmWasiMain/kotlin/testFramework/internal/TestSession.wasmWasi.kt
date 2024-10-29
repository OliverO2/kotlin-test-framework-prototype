package testFramework.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import testFramework.AbstractTestSuite
import testFramework.TestElement
import testFramework.TestSession
import testFramework.internal.integration.IntellijTestLog

internal actual suspend fun runTests(suites: Array<AbstractTestSuite>) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    configureTestsCatching {
        // WORKAROUND Wasm/WASI: calling delay() silently exits wasmWasiNodeRun
        //     https://github.com/Kotlin/kotlinx.coroutines/issues/4239
        withContext(Dispatchers.Default) { }

        TestSession.global.configure(argumentsBasedElementSelection ?: TestElement.AllInSelection)
    }.onSuccess {
        executeTestsCatching {
            TestSession.global.execute(IntellijTestLog)
        }
    }
}
