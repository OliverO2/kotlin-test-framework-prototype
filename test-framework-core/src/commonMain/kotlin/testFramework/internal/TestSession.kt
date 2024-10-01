package testFramework.internal

import testFramework.TestSuite
import testFramework.testPlatform

internal object TestSession : TestSuite(
    parent = null,
    simpleNameOrNull = "${testPlatform.displayName} session",
    configuration = { isSequential = true }
)

expect suspend fun runTests(vararg suites: Any)
