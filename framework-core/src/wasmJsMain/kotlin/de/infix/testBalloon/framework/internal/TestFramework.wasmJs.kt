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
