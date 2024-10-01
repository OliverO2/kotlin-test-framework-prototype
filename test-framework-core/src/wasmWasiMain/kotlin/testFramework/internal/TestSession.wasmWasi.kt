package testFramework.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import testFramework.internal.integration.IntellijTestLog

actual suspend fun runTests(vararg suites: Any) {
    // `suites` is unused because test suites register themselves with `TestSession`.

    // WORKAROUND Wasm/WASI: calling delay() silently exits wasmWasiNodeRun
    //     https://github.com/Kotlin/kotlinx.coroutines/issues/4239
    withContext(Dispatchers.Default) { }

    TestSession.configure()
    TestSession.execute(IntellijTestLog::add)
}
