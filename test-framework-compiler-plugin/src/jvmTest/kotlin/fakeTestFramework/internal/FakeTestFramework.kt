@file:Suppress("PackageDirectoryMismatch", "unused")

// The compiler plugin requires this package name.

package testFramework.internal

import testFramework.AbstractTestSession
import testFramework.AbstractTestSuite
import testFramework.TestFrameworkInvokedByGeneratedCode

@TestFrameworkInvokedByGeneratedCode
internal fun initializeTestFramework(testSession: AbstractTestSession?, arguments: Array<String>? = null) {}

@Suppress("RedundantSuspendModifier")
@TestFrameworkInvokedByGeneratedCode
internal suspend fun configureAndExecuteTests(suites: Array<AbstractTestSuite>) {}
