package testFramework.internal

import testFramework.TestSuite
import testFramework.testPlatform

internal object TestSession : TestSuite<Nothing>(
    parent = null,
    simpleNameOrNull = "${testPlatform.displayName} session",
    configuration = { isSequential = true }
)
