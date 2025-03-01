package testFramework.internal

import testFramework.AbstractTestSuite
import testFramework.TestFrameworkInvokedByGeneratedCode

/**
 * The result of a compilation module's test discovery, used internally by with framework-generated code.
 */
class TestFrameworkDiscoveryResult @TestFrameworkInvokedByGeneratedCode constructor(
    val topLevelTestSuites: Array<AbstractTestSuite>
)
