package de.infix.testBalloon.framework.internal

import kotlinx.coroutines.await
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.time.Duration

internal actual suspend fun TestScope.runTestAwaitingCompletion(
    timeout: Duration,
    action: suspend TestScope.() -> Unit
) {
    @Suppress("CAST_NEVER_SUCCEEDS")
    (runTest(timeout = timeout) { action() } as Promise<JsAny>).await<Unit>()
}

internal actual fun handleFrameworkLevelError(throwable: Throwable) {
    if (runsInBrowser) {
        // TODO: Make configuration errors appear in the log with Wasm/JS.
        //     Throwing at this point triggers "Disconnected (0 times) , because no message in 30000 ms."
        //     without a meaningful error message.
        //     Possibly related:
        //     - Karma logs not always captured, leading to flaky failures in JS/WASM tests
        //       KT-73911 https://youtrack.jetbrains.com/issue/KT-73911
    } else {
        printStderr(throwable.stackTraceToString())
        exitProcess(3)
    }
}

@Suppress("unused")
private fun printStderr(message: String) {
    js("process.stderr.write(message)")
}

@Suppress("unused")
private fun exitProcess(status: Int) {
    js("process.exit(status)")
}
