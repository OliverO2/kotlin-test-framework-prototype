package testFramework.internal.integration

import testFramework.TestScope
import testFramework.internal.TestSession

suspend fun runTests(@Suppress("UNUSED_PARAMETER") vararg scopes: TestScope) {
    // `scopes` is unused because top-level test scopes register themselves with their root scope
    TestSession.configure()
    TestSession.execute(IntellijTestLog::add)
}
